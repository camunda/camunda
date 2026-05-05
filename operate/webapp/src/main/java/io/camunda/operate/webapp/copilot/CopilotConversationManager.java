/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.copilot;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.camunda.operate.webapp.copilot.dto.AgentEvent;
import io.camunda.operate.webapp.copilot.llm.CopilotProperties;
import io.camunda.operate.webapp.copilot.tools.CopilotToolRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class CopilotConversationManager {

  private static final Logger LOG = LoggerFactory.getLogger(CopilotConversationManager.class);
  private static final int MAX_TOOL_TURNS = 8;

  private final Map<String, ConversationSession> sessions = new ConcurrentHashMap<>();
  private final ExecutorService worker = Executors.newCachedThreadPool();

  private final StreamingChatModel chatModel;
  private final CopilotToolRegistry toolRegistry;
  private final CopilotProperties properties;

  public CopilotConversationManager(
      StreamingChatModel chatModel,
      CopilotToolRegistry toolRegistry,
      CopilotProperties properties) {
    this.chatModel = chatModel;
    this.toolRegistry = toolRegistry;
    this.properties = properties;
  }

  public SseEmitter subscribe(String conversationId) {
    final SseEmitter emitter = new SseEmitter(0L);
    final ConversationSession session =
        sessions.computeIfAbsent(conversationId, ConversationSession::new);
    session.attach(emitter);
    emitter.onCompletion(() -> session.detach(emitter));
    emitter.onTimeout(() -> session.detach(emitter));
    emitter.onError(t -> session.detach(emitter));
    return emitter;
  }

  public void handleUserMessage(String conversationId, String content) {
    final ConversationSession session =
        sessions.computeIfAbsent(conversationId, ConversationSession::new);
    session.appendUser(content);
    worker.submit(() -> runAgentLoop(session));
  }

  public void halt(String conversationId) {
    final ConversationSession session = sessions.get(conversationId);
    if (session != null) {
      session.cancel();
    }
  }

  private void runAgentLoop(ConversationSession session) {
    try {
      for (int turn = 0; turn < MAX_TOOL_TURNS; turn++) {
        if (session.isCancelled()) {
          break;
        }
        final ChatResponse response = streamOnce(session);
        final AiMessage ai = response.aiMessage();
        session.appendAi(ai);

        if (!ai.hasToolExecutionRequests()) {
          session.emit(AgentEvent.executionComplete(session.id()));
          return;
        }

        for (ToolExecutionRequest request : ai.toolExecutionRequests()) {
          session.emit(
              AgentEvent.toolInvoke(
                  session.id(), request.name(), request.id(), request.arguments()));
          final String result = toolRegistry.invoke(request.name(), request.arguments());
          session.emit(
              AgentEvent.toolResult(session.id(), request.name(), request.id(), result, true));
          session.appendToolResult(request, result);
        }
      }
      session.emit(AgentEvent.error(session.id(), "Reached max tool turns"));
    } catch (Exception e) {
      LOG.warn("Copilot agent loop failed for {}", session.id(), e);
      session.emit(AgentEvent.error(session.id(), e.getMessage()));
    }
  }

  private ChatResponse streamOnce(ConversationSession session) throws InterruptedException {
    final CountDownLatch done = new CountDownLatch(1);
    final AtomicReference<ChatResponse> result = new AtomicReference<>();
    final AtomicReference<Throwable> failure = new AtomicReference<>();

    chatModel.chat(
        ChatRequest.builder()
            .messages(session.messagesWithSystem(properties.getSystemPrompt()))
            .toolSpecifications(toolRegistry.specifications())
            .build(),
        new StreamingChatResponseHandler() {
          @Override
          public void onPartialResponse(String partial) {
            session.emit(AgentEvent.thinking(session.id(), partial, false));
          }

          @Override
          public void onCompleteResponse(ChatResponse response) {
            result.set(response);
            done.countDown();
          }

          @Override
          public void onError(Throwable error) {
            failure.set(error);
            done.countDown();
          }
        });

    done.await();
    if (failure.get() != null) {
      throw new RuntimeException(failure.get());
    }
    return result.get();
  }

  static final class ConversationSession {
    private final String id;
    private final List<ChatMessage> history = new ArrayList<>();
    private final List<SseEmitter> emitters = new ArrayList<>();
    private volatile boolean cancelled;

    ConversationSession(String id) {
      this.id = id;
    }

    String id() {
      return id;
    }

    synchronized void attach(SseEmitter emitter) {
      emitters.add(emitter);
    }

    synchronized void detach(SseEmitter emitter) {
      emitters.remove(emitter);
    }

    synchronized void appendUser(String content) {
      history.add(UserMessage.from(content));
    }

    synchronized void appendAi(AiMessage message) {
      history.add(message);
    }

    synchronized void appendToolResult(ToolExecutionRequest request, String result) {
      history.add(ToolExecutionResultMessage.from(request, result));
    }

    synchronized List<ChatMessage> messagesWithSystem(String systemPrompt) {
      final List<ChatMessage> withSystem = new ArrayList<>(history.size() + 1);
      withSystem.add(SystemMessage.from(systemPrompt));
      withSystem.addAll(history);
      return withSystem;
    }

    void cancel() {
      cancelled = true;
    }

    boolean isCancelled() {
      return cancelled;
    }

    void emit(AgentEvent event) {
      synchronized (this) {
        for (SseEmitter emitter : new ArrayList<>(emitters)) {
          try {
            emitter.send(SseEmitter.event().data(event));
          } catch (Exception e) {
            emitters.remove(emitter);
          }
        }
      }
    }
  }
}
