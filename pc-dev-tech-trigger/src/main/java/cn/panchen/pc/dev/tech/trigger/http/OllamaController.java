package cn.panchen.pc.dev.tech.trigger.http;

import cn.panchen.pc.dev.tech.api.IAiService;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatClient;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController()
@CrossOrigin("*")
@RequestMapping("/api/v1/ollama/")
public class OllamaController implements IAiService {

    /**
     * Spring AI 提供对话的接口非常简单，call 是直接应答，stream 是流式应答。流式应答通过 Flux 返回。
     *
     * Project Reactor 是一个用于构建响应式应用程序的库，Flux 是 Reactor 中的一个核心组件，用于表示一个异步序列，可以发出 0 到 N 个元素，并且可以是有限的或无限的流。
     * 场景差异化设计：
     * 普通接口：适合短问答、计算类请求
     * 使用场景：1+1=?, 简单分类任务
     * 技术实现：chatClient.call()
     *
     * 流式接口：适合长文本生成、对话场景
     * 使用场景：文章写作、复杂推理
     * 技术实现：chatClient.stream()
     *
     * 设计思想：根据业务场景提供最合适的技术方案，不一刀切
     *
     * 为什么使用Flux而不是CompletableFuture？
     * 技术选型对比：
     * CompletableFuture：适合单个异步任务，数据一次性返回
     * Flux：适合数据流场景，支持背压和复杂流操作
     *
     * 选择理由：
     * 1. 天然支持数据分块（AI生成是典型的数据流）
     * 2. 与Spring WebFlux生态完美集成
     * 3. 支持背压，防止客户端处理不过来
     */
    @Resource
    private OllamaChatClient chatClient;

    /**
     * 非流式 API
     * http://localhost:8090/api/v1/ollama/generate?model=deepseek-r1:1.5b&message=1+1
     */
    @RequestMapping(value = "generate", method = RequestMethod.GET)
    @Override
    public ChatResponse generate(@RequestParam String model, @RequestParam String message) {
        return chatClient.call(new Prompt(message, OllamaOptions.create().withModel(model)));
    }

    /**
     * 流式 API
     * 流式响应
     * http://localhost:8090/api/v1/ollama/generate_stream?model=deepseek-r1:1.5b&message=hi
     */
    @RequestMapping(value = "generate_stream", method = RequestMethod.GET)
    @Override
    public Flux<ChatResponse> generateStream(@RequestParam String model, @RequestParam String message) {
        // 调用底层LLM客户端的流式接口
        return chatClient.stream(new Prompt(message, OllamaOptions.create().withModel(model)));
        // 响应不是一次性返回的，而是分成多个JSON对象陆续到达
        // [块1], [块2], [块3], ... [块N]
    }

}
