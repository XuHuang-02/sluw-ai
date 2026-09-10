package com.sinosig.sluw.application.controller;

import com.sinosig.sluw.application.commons.entity.AssistantTrackEntity;
import com.sinosig.sluw.application.commons.service.AssistantTrackService;
import com.sinosig.sluw.application.commons.utils.DateUtil;
import com.sinosig.sluw.application.commons.utils.Strings;
import com.sinosig.sluw.application.dto.SubmitMarkRequest;
import jakarta.annotation.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/mark")
public class SubmitMarkController {

    @Resource
    private AssistantTrackService assistantTrackService;

    @PostMapping("/submit")
    public ResponseEntity<String> submitMark(@Valid @RequestBody SubmitMarkRequest request) {
        try {
            String massageId = request.getMessageId();
            if (Strings.isNoEmpty(massageId)){
                AssistantTrackEntity track = new AssistantTrackEntity();
                track.setMassageId(massageId);
                track.setMark(String.valueOf(request.getRating()));
                track.setMarkDetail(request.getFeedbackReason());
                track.setModifyDate(DateUtil.date());
                track.setModifyTime(DateUtil.ftime());
                assistantTrackService.updateMark(track);
            }
            return ResponseEntity.ok("反馈提交成功");
        } catch (Exception e) {
            // 记录日志
            return ResponseEntity.internalServerError()
                    .body("反馈提交失败");
        }
    }
}
