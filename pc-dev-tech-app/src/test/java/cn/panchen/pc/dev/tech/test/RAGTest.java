package cn.panchen.pc.dev.tech.test;

import com.alibaba.fastjson.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatClient;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class RAGTest {

    @Resource
    private OllamaChatClient ollamaChatClient;
    @Resource
    private TokenTextSplitter tokenTextSplitter;
    @Resource
    private SimpleVectorStore simpleVectorStore;
    @Resource
    private PgVectorStore pgVectorStore;

    /**
     *  上传 “知识”
     */
    @Test
    public void upload() {
        // 读取文件
        TikaDocumentReader reader = new TikaDocumentReader("./data/file.text");

        List<Document> documents = reader.get();
        // 切割文件，知道每一个向量在哪个位置
        List<Document> documentSplitterList = tokenTextSplitter.apply(documents);

        // 打标，针对上传的文件，有针对性的检索知识库
        documents.forEach(doc -> doc.getMetadata().put("knowledge", "RAG 知识库 潘晨 个人信息"));
        documentSplitterList.forEach(doc -> doc.getMetadata().put("knowledge", "RAG 知识库 潘晨 个人信息"));

        // 用 pgsql 存储切割之后的向量
        pgVectorStore.accept(documentSplitterList);
        log.info("documentSplitterList:" + documentSplitterList);
        log.info("上传完成");
    }

    /**
     * 上传完成之后，要去使用知识库
     */
    @Test
    public void chat() {
        // 对应知识库中的知识
        String message = "潘晨，哪年出生";

        String SYSTEM_PROMPT = """
                Use the information from the DOCUMENTS section to provide accurate answers but act as if you knew this information innately.
                If unsure, simply state that you don't know.
                Another thing you need to note is that your reply must be in Chinese!
                DOCUMENTS:
                    {documents}
                """;

        /**
         * 用知识库要去检索信息
         */
        SearchRequest request = SearchRequest.query(message).withTopK(5).withFilterExpression("knowledge == 'RAG 知识库 潘晨 个人信息'");

        // 使用向量库去搜索
        List<Document> documents = pgVectorStore.similaritySearch(request);
        String documentsCollectors = documents.stream().map(Document::getContent).collect(Collectors.joining());

        // 把模版转换进去
        Message ragMessage = new SystemPromptTemplate(SYSTEM_PROMPT).createMessage(Map.of("documents", documentsCollectors));

        ArrayList<Message> messages = new ArrayList<>();
        messages.add(new UserMessage(message));
        messages.add(ragMessage);

        // 直接单次对话就可以，测试不用流式输出
        ChatResponse chatResponse = ollamaChatClient.call(new Prompt(messages, OllamaOptions.create().withModel("deepseek-r1:1.5b")));

        log.info("RAGTest测试结果:{}", JSON.toJSONString(chatResponse));

    }

}
