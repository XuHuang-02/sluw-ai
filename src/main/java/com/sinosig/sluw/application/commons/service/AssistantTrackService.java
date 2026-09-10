package com.sinosig.sluw.application.commons.service;

import com.sinosig.sluw.application.commons.datasource.dao.DAO;
import com.sinosig.sluw.application.commons.entity.AssistantTrackEntity;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class AssistantTrackService {

    private static final Logger logger = LoggerFactory.getLogger(AssistantTrackService.class);

    @Resource
    private DAO dao;

    /**
     * 异步写入轨迹
     */
    public void save(AssistantTrackEntity assistantTrack) {
        CompletableFuture.runAsync(() -> {
            try {
                dao.save(assistantTrack);
                //dao.save("mybatis.generated.AssistantTrackMapper.save",assistantTrack);
            } catch (Exception e) {
                logger.error("异步保存数据失败", e);
            }
        });
    }

    /**
     * 异步更新轨迹
     */
    public void updateMark(AssistantTrackEntity assistantTrack) {
        CompletableFuture.runAsync(() -> {
            try {
                //dao.update(assistantTrack);
                dao.update("mybatis.mapper.AssistantTrackMapper.updateMark",assistantTrack);
            } catch (Exception e) {
                logger.error("异步保存数据失败", e);
            }
        });
    }
}
