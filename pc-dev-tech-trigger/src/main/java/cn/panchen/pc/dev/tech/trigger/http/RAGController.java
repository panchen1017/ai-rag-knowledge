package cn.panchen.pc.dev.tech.trigger.http;

import cn.panchen.pc.dev.tech.api.IRAGService;
import cn.panchen.pc.dev.tech.api.response.Response;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.redisson.api.RList;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatClient;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.core.io.PathResource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

@Slf4j
@RestController()
@CrossOrigin("*")
@RequestMapping("/api/v1/rag/")
public class RAGController implements IRAGService {

    @Resource
    private OllamaChatClient ollamaChatClient;
    @Resource
    private TokenTextSplitter tokenTextSplitter;
    @Resource
    private SimpleVectorStore simpleVectorStore;
    @Resource
    private PgVectorStore pgVectorStore;
    @Resource
    private RedissonClient redissonClient;

    /**
     * 本地测试用的 RAG 知识库能力”，正式封装成一套 HTTP 接口服务
     * 支持前端上传知识库、查询已有知识库标签、以及在对话时选择具体知识库做 RAG 流式对话。
     * @return
     */
    @RequestMapping(value = "query_rag_tag_list", method = RequestMethod.GET)
    @Override
    public Response<List<String>> queryRagTagList() {
        /**
         * 查询目前 redis 中存放了多少 ragTag 标签的知识库
         */
        RList<String> elements = redissonClient.getList("ragTag");
        return Response.<List<String>>builder()
                .code("0000")
                .info("调用成功")
                .data(elements)
                .build();
    }

    /**
     *  上传知识库文件接口，根据 ragTag，上传不同知识库的文件
     *  例：
     *      ragTag： ----------知识库文件----------
     *      Java开发：《SpringBoot AI》、《MVC架构》
     *      旅行：《北京一日游》、《上海周边三日游》
     */
    @RequestMapping(value = "file/upload", method = RequestMethod.POST, headers = "content-type=multipart/form-data")
    @Override
    public Response<String> uploadFile(@RequestParam String ragTag, @RequestParam("file") List<MultipartFile> files) {
        log.info("-----------开始上传知识库 ragtag:{}----------", ragTag);
        for (MultipartFile file: files) {
            TikaDocumentReader reader = new TikaDocumentReader(file.getResource());
            List<Document> documents = reader.get();
            // 切割文件，知道每一个向量在哪个位置
            List<Document> documentSplitterList = tokenTextSplitter.apply(documents);
            // 打标，针对上传的文件，有针对性的检索知识库
            documents.forEach(doc -> doc.getMetadata().put("knowledge", ragTag));
            documentSplitterList.forEach(doc -> doc.getMetadata().put("knowledge", ragTag));
            // 用 pgsql 存储切割之后的向量
            pgVectorStore.accept(documentSplitterList);


            RList<String> elements = redissonClient.getList("ragTag");
            // 之前就有更新即可
            if (!elements.contains(ragTag)){
                elements.add(ragTag);
            }

        }
        log.info("-----------上传知识库完成：{}-----------", ragTag);
        return Response.<String>builder().code("0000").info("调用成功").build();
    }

    /**
     * 分析 Git 代码
     * 1. 克隆仓库 → 2. 遍历文件 → 3. 解析内容 → 4. 向量化存储
     * 填写仓库url，用户名，token后，就将代码拉下来并且上传对应的知识库
     * @param repoUrl：仓库 url
     * @param userName：用户名
     * @param token：token
     * @return
     * @throws Exception
     */
    @RequestMapping(value = "analyze_git_repository", method = RequestMethod.POST)
    @Override
    public Response<String> analyzeGitRepository(@RequestParam String repoUrl, @RequestParam String userName, @RequestParam String token) throws Exception {
        String localPath = "git-cloned-repo";
        // 分割下工程名称
        // 例如：https://github.com/panchen1017/ai-rag-knowledge
        // 取出：ai-rag-knowledge 作为 RAG 的 tag
        String repoProjectName = extractProjectName(repoUrl);
        log.info("克隆路径：{}", new File(localPath).getAbsolutePath());

        // 先删除本来文件夹中的代码
        FileUtils.deleteDirectory(new File(localPath));
        // 拉代码
        Git git = Git.cloneRepository()
                .setURI(repoUrl)
                .setDirectory(new File(localPath))
                .setCredentialsProvider(new UsernamePasswordCredentialsProvider(userName, token))
                .call();

        /**
         * Git 仓库下文件结构嵌套很深（多模块、多层目录），用 walkFileTree 可以统一处理所有文件，同时对异常/无法访问的文件统一兜底。
         */
        // 上传知识库
        Files.walkFileTree(Paths.get(localPath), new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                log.info("{} 遍历解析路径，上传知识库:{}", repoProjectName, file.getFileName());
                try {
                    TikaDocumentReader reader = new TikaDocumentReader(new PathResource(file));
                    List<Document> documents = reader.get();
                    List<Document> documentSplitterList = tokenTextSplitter.apply(documents);
                    documents.forEach(doc -> doc.getMetadata().put("knowledge", repoProjectName));

                    documentSplitterList.forEach(doc -> doc.getMetadata().put("knowledge", repoProjectName));
                    pgVectorStore.accept(documentSplitterList);
                } catch (Exception e) {
                    log.error("遍历解析路径，上传知识库失败:{}", file.getFileName());
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                log.info("Failed to access file: {} - {}", file.toString(), exc.getMessage());
                return FileVisitResult.CONTINUE;
            }
        });
//        FileUtils.deleteDirectory(new File(localPath));

        // 添加新的工程名称：ai-rag-knowledge
        RList<String> elements = redissonClient.getList("ragTag");
        if (!elements.contains(repoProjectName)) {
            elements.add(repoProjectName);
        }

        git.close();
        log.info("遍历解析路径，上传完成:{}", repoUrl);
        return Response.<String>builder().code("0000").info("调用成功").build();
    }

    private String extractProjectName(String repoUrl) {
        String[] parts = repoUrl.split("/");
        String projectNameWithGit = parts[parts.length - 1];
        return projectNameWithGit.replace(".git", "");
    }
}
