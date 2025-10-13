/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.usertask;

import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.zeebe.engine.processing.AsyncRequestBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.common.EventHandle;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.PermissionsBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.processing.usertask.processors.UserTaskAssignProcessor;
import io.camunda.zeebe.engine.processing.usertask.processors.UserTaskCancelProcessor;
import io.camunda.zeebe.engine.processing.usertask.processors.UserTaskClaimProcessor;
import io.camunda.zeebe.engine.processing.usertask.processors.UserTaskCommandProcessor;
import io.camunda.zeebe.engine.processing.usertask.processors.UserTaskCompleteProcessor;
import io.camunda.zeebe.engine.processing.usertask.processors.UserTaskCreateProcessor;
import io.camunda.zeebe.engine.processing.usertask.processors.UserTaskUpdateProcessor;
import io.camunda.zeebe.engine.processing.usertask.processors.auth.UserTaskPermissionsBehavior;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public final class UserTaskCommandProcessors {

  private final Map<UserTaskIntent, UserTaskCommandProcessor> commandToProcessor;

  public UserTaskCommandProcessors(
      final SecurityConfiguration securityConfig,
      final ProcessingState processingState,
      final KeyGenerator keyGenerator,
      final BpmnBehaviors bpmnBehaviors,
      final Writers writers,
      final AsyncRequestBehavior asyncRequestBehavior,
      final AuthorizationCheckBehavior authCheckBehavior) {
    final EventHandle eventHandle =
        new EventHandle(
            keyGenerator,
            processingState.getEventScopeInstanceState(),
            writers,
            processingState.getProcessState(),
            bpmnBehaviors.eventTriggerBehavior(),
            bpmnBehaviors.stateBehavior());

    final UserTaskPermissionsBehavior taskPermissionsBehavior =
        new UserTaskPermissionsBehavior(
            keyGenerator,
            writers.command(),
            new PermissionsBehavior(processingState.getAuthorizationState(), authCheckBehavior),
            securityConfig);

    commandToProcessor =
        new EnumMap<>(
            Map.of(
                UserTaskIntent.CREATE,
                new UserTaskCreateProcessor(
                    processingState,
                    writers,
                    bpmnBehaviors.userTaskBehavior(),
                    bpmnBehaviors.jobBehavior(),
                    taskPermissionsBehavior),
                UserTaskIntent.ASSIGN,
                new UserTaskAssignProcessor(
                    processingState,
                    writers,
                    asyncRequestBehavior,
                    authCheckBehavior,
                    taskPermissionsBehavior),
                UserTaskIntent.CLAIM,
                new UserTaskClaimProcessor(
                    processingState, writers, asyncRequestBehavior, authCheckBehavior),
                UserTaskIntent.UPDATE,
                new UserTaskUpdateProcessor(
                    processingState,
                    writers,
                    bpmnBehaviors.variableBehavior(),
                    asyncRequestBehavior,
                    authCheckBehavior),
                UserTaskIntent.COMPLETE,
                new UserTaskCompleteProcessor(
                    processingState, eventHandle, writers, asyncRequestBehavior, authCheckBehavior),
                UserTaskIntent.CANCEL,
                new UserTaskCancelProcessor(processingState, writers)));
    validateProcessorsSetup(commandToProcessor);
  }

  public UserTaskCommandProcessor getCommandProcessor(final UserTaskIntent userTaskIntent) {
    if (userTaskIntent.isEvent()) {
      throw new IllegalArgumentException(
          "Expected a command, but received an event: '%s'. Valid UserTask commands are: %s"
              .formatted(userTaskIntent, UserTaskIntent.commands()));
    }

    return Optional.ofNullable(commandToProcessor.get(userTaskIntent))
        .orElseThrow(
            () ->
                new UnsupportedOperationException(
                    "No processor found for the '%s' UserTask command".formatted(userTaskIntent)));
  }

  private static void validateProcessorsSetup(
      final Map<UserTaskIntent, UserTaskCommandProcessor> commandToProcessor) {
    final var missingProcessors =
        UserTaskIntent.commands().stream()
            // Exclude COMPLETE_TASK_LISTENER and REJECT_TASK_LISTENER as they don't require a
            // dedicated processor.
            // This intent is handled internally within UserTaskProcessor
            .filter(intent -> intent != UserTaskIntent.COMPLETE_TASK_LISTENER)
            .filter(intent -> intent != UserTaskIntent.DENY_TASK_LISTENER)
            .filter(intent -> !commandToProcessor.containsKey(intent))
            .collect(Collectors.toSet());

    if (!missingProcessors.isEmpty()) {
      throw new IllegalStateException(
          "No processors defined for the following UserTask commands: %s"
              .formatted(missingProcessors));
    }
  }
}
