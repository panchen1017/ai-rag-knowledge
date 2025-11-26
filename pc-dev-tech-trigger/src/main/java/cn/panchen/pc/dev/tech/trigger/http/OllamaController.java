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
