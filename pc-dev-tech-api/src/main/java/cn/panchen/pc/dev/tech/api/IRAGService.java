package cn.panchen.pc.dev.tech.api;

import cn.panchen.pc.dev.tech.api.response.Response;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface IRAGService {

    /**
     * 返回 List 类型的数据
     * @return
     */
    Response<List<String>> queryRagTagList();

    /**
     * 上传 RAG 文件
     * @param ragTag
     * @param files
     * @return
     */
    Response<String> uploadFile(String ragTag, List<MultipartFile> files);

}
