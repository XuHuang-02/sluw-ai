package com.sinosig.sluw.application.controller;

import com.sinosig.sluw.application.service.DeepSeekChatService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/chat")
public class ChatTestController {

    @Autowired
    private DeepSeekChatService chatService;

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(@RequestBody String userInput) {
        return chatService.streamChat(userInput);
    }
}
