package cn.panchen.pc.dev.tech.trigger.http;

import cn.panchen.pc.dev.tech.api.IRAGService;
import cn.panchen.pc.dev.tech.api.response.Response;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RList;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatClient;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    @RequestMapping(value = "file/upload", method = RequestMethod.POST, headers = "content-type=multipart/form-data")
    @Override
    public Response<String> uploadFile(@RequestParam String ragTag, @RequestParam("file") List<MultipartFile> files) {
        /**
         *  上传知识库文件接口，根据 ragTag，上传不同知识库的文件
         *  例：
         *      ragTag： ----------知识库文件----------
         *      Java开发：《SpringBoot AI》、《MVC架构》
         *      旅行：《北京一日游》、《上海周边三日游》
         */
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








}
