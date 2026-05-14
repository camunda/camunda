/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.copilot;

import io.camunda.operate.webapp.copilot.dto.SendMessagePayload;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.api.model.CamundaAuthentication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/v2/copilot/conversations")
@ConditionalOnProperty(prefix = "camunda.operate.copilot", name = "api-key")
public class CopilotController {

  private final CopilotConversationManager conversations;
  private final CamundaAuthenticationProvider authenticationProvider;

  public CopilotController(
      CopilotConversationManager conversations,
      CamundaAuthenticationProvider authenticationProvider) {
    this.conversations = conversations;
    this.authenticationProvider = authenticationProvider;
  }

  @GetMapping(value = "/{conversationId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter subscribe(@PathVariable String conversationId) {
    return conversations.subscribe(conversationId);
  }

  @PostMapping("/{conversationId}/messages")
  public ResponseEntity<Void> sendMessage(
      @PathVariable String conversationId, @RequestBody SendMessagePayload payload) {
    final CamundaAuthentication auth = authenticationProvider.getCamundaAuthentication();
    conversations.handleUserMessage(conversationId, auth, payload.content(), payload.context());
    return ResponseEntity.accepted().build();
  }

  @PostMapping("/{conversationId}/halt")
  public ResponseEntity<Void> halt(@PathVariable String conversationId) {
    conversations.halt(conversationId);
    return ResponseEntity.noContent().build();
  }
}
