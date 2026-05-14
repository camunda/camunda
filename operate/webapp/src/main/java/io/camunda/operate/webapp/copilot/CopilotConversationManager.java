/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.copilot;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import io.camunda.security.api.model.CamundaAuthentication;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
@ConditionalOnProperty(prefix = "camunda.operate.copilot", name = "api-key")
public class CopilotConversationManager {

  private static final Logger LOG = LoggerFactory.getLogger(CopilotConversationManager.class);
  private static final int MAX_TOOL_TURNS = 8;

  private final Map<String, ConversationSession> sessions = new ConcurrentHashMap<>();
  private final ExecutorService worker = Executors.newCachedThreadPool();

  private final StreamingChatModel chatModel;
  private final CopilotToolRegistry toolRegistry;
  private final CopilotProperties properties;
  private final ObjectMapper objectMapper;

  public CopilotConversationManager(
      StreamingChatModel chatModel,
      CopilotToolRegistry toolRegistry,
      CopilotProperties properties,
      ObjectMapper objectMapper) {
    this.chatModel = chatModel;
    this.toolRegistry = toolRegistry;
    this.properties = properties;
    this.objectMapper = objectMapper;
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

  public void handleUserMessage(
      String conversationId,
      CamundaAuthentication auth,
      String content,
      Map<String, Object> context) {
    final ConversationSession session =
        sessions.computeIfAbsent(conversationId, ConversationSession::new);
    session.appendUser(content);
    final var safeContext = context != null ? Map.copyOf(context) : Map.<String, Object>of();
    worker.submit(() -> runAgentLoop(session, auth, safeContext));
  }

  public void halt(String conversationId) {
    final ConversationSession session = sessions.get(conversationId);
    if (session != null) {
      session.cancel();
    }
  }

  private void runAgentLoop(
      ConversationSession session, CamundaAuthentication auth, Map<String, Object> context) {
    try {
      for (int turn = 0; turn < MAX_TOOL_TURNS; turn++) {
        if (session.isCancelled()) {
          break;
        }
        final ChatResponse response = streamOnce(session, context);
        final AiMessage ai = response.aiMessage();
        session.appendAi(ai);

        if (!ai.hasToolExecutionRequests()) {
          final String finalText = ai.text() != null ? ai.text() : "";
          session.emit(AgentEvent.executionComplete(session.id(), finalText));
          return;
        }

        for (ToolExecutionRequest request : ai.toolExecutionRequests()) {
          session.emit(
              AgentEvent.toolInvoke(
                  session.id(), request.name(), request.id(), request.arguments()));
          String resultJson;
          boolean success;
          try {
            resultJson = toolRegistry.invoke(request.name(), request.arguments(), auth);
            success = true;
          } catch (Exception e) {
            LOG.warn(
                "Copilot tool '{}' failed for conversation {}: {}",
                request.name(),
                session.id(),
                e.getMessage());
            resultJson = errorJson(e.getMessage());
            success = false;
          }
          session.emit(
              AgentEvent.toolResult(
                  session.id(), request.name(), request.id(), resultJson, success));
          session.appendToolResult(request, resultJson);
        }
      }
      session.emit(AgentEvent.error(session.id(), "Reached max tool turns"));
    } catch (Exception e) {
      LOG.warn("Copilot agent loop failed for {}", session.id(), e);
      session.emit(AgentEvent.error(session.id(), e.getMessage()));
    }
  }

  private String errorJson(String message) {
    try {
      return objectMapper.writeValueAsString(Map.of("error", message != null ? message : ""));
    } catch (Exception e) {
      return "{\"error\":\"" + (message != null ? message.replace("\"", "'") : "") + "\"}";
    }
  }

  private String buildInstanceContext(Map<String, Object> context) {
    final var parts = new ArrayList<String>();

    final Object instanceId = context.get("processInstanceId");
    if (instanceId != null) {
      parts.add(
          "The user is currently viewing process instance "
              + instanceId
              + ". When they refer to 'this instance', 'this process', or use deictic pronouns, "
              + "they mean this instance. Default to answering in that context unless they explicitly "
              + "ask about something else.");
    }

    if (context.get("incident") instanceof Map<?, ?> incident) {
      final Object incidentKey = incident.get("incidentKey");
      final Object errorType = incident.get("errorType");
      final Object errorMessage = incident.get("errorMessage");
      final Object elementName = incident.get("elementName");
      parts.add(
          "The user is asking about incident "
              + incidentKey
              + " (errorType="
              + errorType
              + ") on element '"
              + elementName
              + "': \""
              + errorMessage
              + "\". Explain in plain language what this error means and why it commonly happens, "
              + "then give concrete recommended steps to resolve it. If the user asks a follow-up "
              + "that needs more context, use the available tools to fetch additional details about "
              + "the instance or other incidents.");
    }

    return parts.isEmpty() ? null : String.join("\n\n", parts);
  }

  private ChatResponse streamOnce(ConversationSession session, Map<String, Object> context)
      throws InterruptedException {
    final CountDownLatch done = new CountDownLatch(1);
    final AtomicReference<ChatResponse> result = new AtomicReference<>();
    final AtomicReference<Throwable> failure = new AtomicReference<>();

    chatModel.chat(
        ChatRequest.builder()
            .messages(
                session.messagesWithSystem(
                    properties.getSystemPrompt(), buildInstanceContext(context)))
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

    synchronized List<ChatMessage> messagesWithSystem(String systemPrompt, String instanceContext) {
      final boolean hasContext = instanceContext != null && !instanceContext.isBlank();
      final List<ChatMessage> withSystem = new ArrayList<>(history.size() + (hasContext ? 2 : 1));
      withSystem.add(SystemMessage.from(systemPrompt));
      if (hasContext) {
        withSystem.add(SystemMessage.from(instanceContext));
      }
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
