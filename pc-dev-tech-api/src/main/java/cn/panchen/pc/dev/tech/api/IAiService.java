package cn.panchen.pc.dev.tech.api;

import org.springframework.ai.chat.ChatResponse;
import reactor.core.publisher.Flux;

public interface IAiService {

    // 非流式接口
    ChatResponse generate(String model, String message);

    /**
     * 一个核心场景是：用户提出问题，系统需要从知识库中检索相关文档片段（Retrieval），将其与问题组合成提示词，然后发送给大语言模型（LLM）来生成答案（Generation）。
     * LLM生成文本，尤其是长文本，是需要时间的。如果等到整个答案都生成完毕再一次性返回给前端，用户会经历一个漫长的等待，然后突然看到全部答案，体验非常差。
     * @param model
     * @param message
     * @return
     */
    // 流式接口
    // 答案像真人对话一样，一个字一个字地、或者一个词一个词地“流”出来。用户可以几乎无延迟地开始阅读答案的开头部分，而不必等待整个答案生成。
    Flux<ChatResponse> generateStream(String model, String message);

}
