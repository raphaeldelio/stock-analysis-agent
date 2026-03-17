package com.redis.stockanalysisagent.chat.api;

import com.redis.stockanalysisagent.chat.StockAnalysisChatService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final StockAnalysisChatService stockAnalysisChatService;
    private final String defaultUserId;

    public ChatController(
            StockAnalysisChatService stockAnalysisChatService,
            @Value("${app.chat.user-id:workshop-user}") String defaultUserId
    ) {
        this.stockAnalysisChatService = stockAnalysisChatService;
        this.defaultUserId = defaultUserId;
    }

    @PostMapping
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        String sessionId = normalizeSessionId(request.sessionId());
        StockAnalysisChatService.ChatTurn turn = stockAnalysisChatService.chat(
                defaultUserId,
                sessionId,
                request.message().trim()
        );

        return ResponseEntity.ok(new ChatResponse(
                sessionId,
                turn.conversationId(),
                turn.response(),
                turn.retrievedMemories()
        ));
    }

    @DeleteMapping("/session/{sessionId}")
    public ResponseEntity<Void> clearSession(@PathVariable String sessionId) {
        stockAnalysisChatService.clearSession(defaultUserId, sessionId);
        return ResponseEntity.noContent().build();
    }

    private String normalizeSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return UUID.randomUUID().toString();
        }

        return sessionId.trim();
    }
}
