/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.agentinstance;

import io.camunda.security.core.auth.RequiredAuthorization;
import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.agentinstance.AgentHistoryBatchBehavior.LeaseMismatchHandling;
import io.camunda.zeebe.engine.processing.identity.AuthorizationRejectionMapper;
import io.camunda.zeebe.engine.processing.identity.authorization.CslAuthorizationCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.SuspensionAware;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.AgentDefinitionState;
import io.camunda.zeebe.engine.state.immutable.AgentHistoryState;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.agentinstance.AgentInstanceRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.AgentHistoryIntent;
import io.camunda.zeebe.protocol.record.intent.AgentInstanceIntent;
import io.camunda.zeebe.protocol.record.mapper.AuthzModelMapper;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRecordValue;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRole;
import io.camunda.zeebe.protocol.record.value.AgentInstanceStatus;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.Either;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public final class AgentInstanceCreateProcessor
    implements TypedRecordProcessor<AgentInstanceRecord>, SuspensionAware<AgentInstanceRecord> {

  // CAUTION: callers may parse this message to extract the existing agentInstanceKey from the
  // second '%d'. Wording changes that alter the position of the numeric values are a breaking
  // contract change — update connector-side parsing logic in sync.
  private static final String ERROR_MSG_AGENT_INSTANCE_ALREADY_EXISTS =
      "Expected to associate element instance with key '%d' with an agent instance, but it is already associated with agent instance with key '%d'.";

  private static final String ERROR_MSG_ELEMENT_INSTANCE_NOT_FOUND =
      "Expected to create agent instance for element instance with key '%d', but no such element instance was found.";
  private static final String ERROR_MSG_ELEMENT_INSTANCE_NOT_ACTIVE =
      "Expected to create agent instance for element instance with key '%d', but it is not active.";
  private static final String ERROR_MSG_UNSUPPORTED_ELEMENT_TYPE =
      "Expected to create agent instance for element instance with key '%d', but its BPMN element type '%s' is not supported. Supported types are: %s.";
  private static final String ERROR_MSG_NO_AGENT_DEFINITION =
      "Expected to create agent instance for element instance with key '%d', but element '%s' has no agent definition. Mark the element with 'zeebe:agentDefinition' at deploy time.";

  private static final List<BpmnElementType> SUPPORTED_ELEMENT_TYPES =
      List.of(BpmnElementType.AD_HOC_SUB_PROCESS, BpmnElementType.SERVICE_TASK);

  private static final String ERROR_MSG_CREATE_ROLE_NOT_ALLOWED =
      "Expected to create agent instance with history item '%s', but its role is '%s'. Allowed "
          + "roles are: %s.";
  private static final String ERROR_MSG_CREATE_METRICS_NOT_ALLOWED =
      "Expected to create agent instance with history item '%s', but it carries non-zero "
          + "token-usage metrics. History items included when creating an agent instance must "
          + "not carry non-zero token-usage metrics; durationMs is exempt.";

  // AgentHistoryMetrics properties default to -1 to mean "not provided" — that sentinel, like 0,
  // must not itself count as a non-zero metric.
  private static final long METRIC_NOT_PROVIDED = -1L;

  private static final Set<AgentHistoryRole> ALLOWED_CREATE_ROLES =
      Set.of(AgentHistoryRole.CONFIGURATION, AgentHistoryRole.USER);

  private final StateWriter stateWriter;
  private final TypedResponseWriter responseWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final ElementInstanceState elementInstanceState;
  private final ProcessState processState;
  private final AgentDefinitionState agentDefinitionState;
  private final AgentHistoryState agentHistoryState;
  private final CslAuthorizationCheck cslCheck;
  private final KeyGenerator keyGenerator;
  private final AgentHistoryBatchBehavior historyBatchHelper;

  public AgentInstanceCreateProcessor(
      final Writers writers,
      final ProcessingState processingState,
      final CslAuthorizationCheck cslCheck,
      final KeyGenerator keyGenerator) {
    stateWriter = writers.state();
    responseWriter = writers.response();
    rejectionWriter = writers.rejection();
    elementInstanceState = processingState.getElementInstanceState();
    processState = processingState.getProcessState();
    agentDefinitionState = processingState.getAgentDefinitionState();
    agentHistoryState = processingState.getAgentHistoryState();
    this.cslCheck = cslCheck;
    this.keyGenerator = keyGenerator;
    historyBatchHelper = new AgentHistoryBatchBehavior(keyGenerator, processingState);
  }

  @Override
  public void processRecord(final TypedRecord<AgentInstanceRecord> command) {
    final var commandValue = command.getValue();
    final var elementInstanceKey = commandValue.getElementInstanceKey();

    final var elementInstance = elementInstanceState.getInstance(elementInstanceKey);
    if (elementInstance == null) {
      writeRejection(
          command,
          RejectionType.NOT_FOUND,
          ERROR_MSG_ELEMENT_INSTANCE_NOT_FOUND.formatted(elementInstanceKey));
      return;
    }

    final var elementInstanceValue = elementInstance.getValue();
    final var isAuthorized =
        cslCheck.checkAuthorizationAndTenant(
            command,
            RequiredAuthorization.of(
                b ->
                    b.resourceType(
                            AuthzModelMapper.fromProtocol(
                                AuthorizationResourceType.PROCESS_DEFINITION))
                        .permissionType(
                            AuthzModelMapper.fromProtocol(PermissionType.UPDATE_PROCESS_INSTANCE))
                        .resourceId(elementInstanceValue.getBpmnProcessId())),
            command.getValue(),
            AuthorizationRejectionMapper.forbidden(
                PermissionType.UPDATE_PROCESS_INSTANCE,
                AuthorizationResourceType.PROCESS_DEFINITION),
            elementInstanceValue.getTenantId(),
            new Rejection(
                RejectionType.NOT_FOUND,
                ERROR_MSG_ELEMENT_INSTANCE_NOT_FOUND.formatted(elementInstanceKey)));
    if (isAuthorized.isLeft()) {
      final var rejection = isAuthorized.getLeft();
      writeRejection(command, rejection.type(), rejection.reason());
      return;
    }

    final var existingAgentInstanceKey = elementInstance.getAgentInstanceKey();
    if (existingAgentInstanceKey != -1L) {
      writeRejection(
          command,
          RejectionType.ALREADY_EXISTS,
          ERROR_MSG_AGENT_INSTANCE_ALREADY_EXISTS.formatted(
              elementInstanceKey, existingAgentInstanceKey));
      return;
    }

    if (!elementInstance.isActive()) {
      writeRejection(
          command,
          RejectionType.INVALID_STATE,
          ERROR_MSG_ELEMENT_INSTANCE_NOT_ACTIVE.formatted(elementInstanceKey));
      return;
    }

    final var bpmnElementType = elementInstanceValue.getBpmnElementType();
    if (!SUPPORTED_ELEMENT_TYPES.contains(bpmnElementType)) {
      writeRejection(
          command,
          RejectionType.INVALID_ARGUMENT,
          ERROR_MSG_UNSUPPORTED_ELEMENT_TYPE.formatted(
              elementInstanceKey, bpmnElementType, SUPPORTED_ELEMENT_TYPES));
      return;
    }

    final var agentDefinitionKey =
        agentDefinitionState.getAgentDefinitionKey(
            elementInstanceValue.getProcessDefinitionKey(),
            elementInstanceValue.getElementIdBuffer());
    if (agentDefinitionKey == null) {
      writeRejection(
          command,
          RejectionType.INVALID_ARGUMENT,
          ERROR_MSG_NO_AGENT_DEFINITION.formatted(
              elementInstanceKey, elementInstanceValue.getElementId()));
      return;
    }

    final var validJob =
        historyBatchHelper.validateJobContext(
            commandValue.getJobKey(),
            commandValue.getJobLease(),
            commandValue.getElementInstanceKey(),
            commandValue.getHistory(),
            LeaseMismatchHandling.REJECT);
    if (validJob.isLeft()) {
      final var rejection = validJob.getLeft();
      writeRejection(command, rejection.type(), rejection.reason());
      return;
    }

    final var isHistoryValid = historyBatchHelper.validateHistory(commandValue.getHistory());
    if (isHistoryValid.isLeft()) {
      final var rejection = isHistoryValid.getLeft();
      writeRejection(command, rejection.type(), rejection.reason());
      return;
    }

    final var isCreateHistoryValid = validateCreateHistoryItems(commandValue.getHistory());
    if (isCreateHistoryValid.isLeft()) {
      final var rejection = isCreateHistoryValid.getLeft();
      writeRejection(command, rejection.type(), rejection.reason());
      return;
    }

    final var deployedProcess =
        processState.getProcessByKeyAndTenant(
            elementInstanceValue.getProcessDefinitionKey(), elementInstanceValue.getTenantId());

    final var agentInstanceKey = keyGenerator.nextKey();
    final var event =
        new AgentInstanceRecord()
            .setAgentInstanceKey(agentInstanceKey)
            .setElementInstanceKey(elementInstanceKey)
            .setElementInstanceKeys(List.of(elementInstanceKey))
            .setElementId(elementInstanceValue.getElementId())
            .setBpmnProcessId(elementInstanceValue.getBpmnProcessId())
            .setProcessInstanceKey(elementInstanceValue.getProcessInstanceKey())
            .setRootProcessInstanceKey(elementInstanceValue.getRootProcessInstanceKey())
            .setProcessDefinitionKey(elementInstanceValue.getProcessDefinitionKey())
            .setProcessDefinitionVersion(elementInstanceValue.getVersion())
            .setAgentDefinitionKey(agentDefinitionKey)
            .setProcessDefinitionVersionTag(
                deployedProcess == null ? "" : deployedProcess.getVersionTag())
            .setTenantId(elementInstanceValue.getTenantId())
            .setStatus(AgentInstanceStatus.INITIALIZING);

    event
        .getDefinition()
        .setModel(commandValue.getDefinition().getModel())
        .setProvider(commandValue.getDefinition().getProvider())
        .setSystemPrompt(commandValue.getDefinition().getSystemPrompt());

    event
        .getLimits()
        .setMaxTokens(commandValue.getLimits().getMaxTokens())
        .setMaxModelCalls(commandValue.getLimits().getMaxModelCalls())
        .setMaxToolCalls(commandValue.getLimits().getMaxToolCalls());

    if (!commandValue.getHistory().isEmpty()) {
      historyBatchHelper.applyInstanceChangesFromHistory(
          event,
          commandValue.getJobKey(),
          commandValue.getJobLease(),
          commandValue.getElementInstanceKey(),
          commandValue.getHistory());
    }

    // event.getHistory() allocates a fresh list on every call — now that
    // applyInstanceChangesFromHistory has populated it above, read it once here and reuse it for
    // both loops below.
    final var history = event.getHistory();

    // Unlike UPDATE (which defers this to AgentHistoryCommitProcessor, once the item is actually
    // committed), CREATE applies a CONFIGURATION item's changes right here, before its own
    // AGENT_INSTANCE:CREATED is appended below — so a newly created instance always starts with a
    // valid, usable definition instead of a placeholder one.
    history.stream()
        .filter(Predicate.not(AgentHistoryRecordValue::isDuplicate))
        .filter(item -> item.getRole() == AgentHistoryRole.CONFIGURATION)
        .forEach(item -> AgentHistoryBatchBehavior.applyConfigurationChanges(event, item));

    stateWriter.appendFollowUpEvent(agentInstanceKey, AgentInstanceIntent.CREATED, event);

    // AGENT_HISTORY items reference their AgentInstance parent by key, so they can only be
    // created after AGENT_INSTANCE:CREATED above has assigned that key.
    history.stream()
        .filter(Predicate.not(AgentHistoryRecordValue::isDuplicate))
        .forEach(
            item -> {
              stateWriter.appendFollowUpEvent(
                  item.getAgentHistoryKey(), AgentHistoryIntent.CREATED, item);
              // CONFIGURATION changes are applied directly above, during CREATE itself, rather
              // than deferred like an UPDATE's would be — so the history record must commit
              // immediately too, instead of sitting PENDING/discardable.
              if (item.getRole() == AgentHistoryRole.CONFIGURATION) {
                // Read the item back from state rather than reusing the untrimmed in-memory
                // `item`: AgentHistoryCreatedApplier, applied synchronously by the CREATED event
                // just appended above, has already trimmed it down to identity fields plus the
                // CONFIGURATION-specific ones — the exact shape AgentHistoryCommitProcessor's own
                // COMMITTED event re-emits for UPDATE. Fetching it keeps both paths' COMMITTED
                // events identical in shape without duplicating that trimming logic here.
                stateWriter.appendFollowUpEvent(
                    item.getAgentHistoryKey(),
                    AgentHistoryIntent.COMMITTED,
                    agentHistoryState.get(item.getAgentHistoryKey()));
              }
            });

    responseWriter.writeAcceptedResponseOnCommand(
        agentInstanceKey, AgentInstanceIntent.CREATED, event, command);
  }

  /**
   * Validates that every item in a CREATE batch has an allowed role ({@link
   * AgentHistoryRole#CONFIGURATION} or {@link AgentHistoryRole#USER}) and carries no non-zero
   * token-usage metrics ({@code inputTokens}, {@code outputTokens}, {@code reasoningTokenCount},
   * {@code cacheCreationTokenCount}, {@code cacheReadTokenCount}); {@code durationMs} is exempt.
   * UPDATE keeps accepting every role, metrics included, since a rejected UPDATE still has a live
   * {@code AgentInstance} to apply metrics onto — a rejected CREATE does not, so this check is
   * CREATE-only.
   *
   * @return the rejection for the first invalid item found, or {@link Either#rightVoid()} if every
   *     item is a CONFIGURATION or USER item without non-zero token-usage metrics
   */
  private static Either<Rejection, Void> validateCreateHistoryItems(
      final List<? extends AgentHistoryRecordValue> history) {
    if (history == null || history.isEmpty()) {
      return Either.rightVoid();
    }

    for (final var item : history) {
      final var historyItemId = item.getHistoryItemId();

      if (!ALLOWED_CREATE_ROLES.contains(item.getRole())) {
        // sorted here, the only place the message's ordering matters: Set's own iteration order
        // is seeded from a per-JVM salt, so its toString() would vary across JVM runs.
        final var sortedAllowedRoles =
            ALLOWED_CREATE_ROLES.stream().sorted(Comparator.comparing(Enum::name)).toList();
        return Either.left(
            new Rejection(
                RejectionType.INVALID_ARGUMENT,
                ERROR_MSG_CREATE_ROLE_NOT_ALLOWED.formatted(
                    historyItemId, item.getRole(), sortedAllowedRoles)));
      }

      if (hasNonZeroMetrics(item.getMetrics())) {
        return Either.left(
            new Rejection(
                RejectionType.INVALID_ARGUMENT,
                ERROR_MSG_CREATE_METRICS_NOT_ALLOWED.formatted(historyItemId)));
      }
    }
    return Either.rightVoid();
  }

  /**
   * {@code durationMs} is deliberately excluded from this check: it isn't an accumulated
   * conversation metric like the others, so it may be non-zero even on an otherwise-clean
   * CONFIGURATION/USER item.
   */
  private static boolean hasNonZeroMetrics(
      final AgentHistoryRecordValue.AgentHistoryMetricsValue metrics) {
    return isNonZeroMetricValue(metrics.getInputTokens())
        || isNonZeroMetricValue(metrics.getOutputTokens())
        || isNonZeroMetricValue(metrics.getReasoningTokenCount())
        || isNonZeroMetricValue(metrics.getCacheCreationTokenCount())
        || isNonZeroMetricValue(metrics.getCacheReadTokenCount());
  }

  private static boolean isNonZeroMetricValue(final long value) {
    return value != 0 && value != METRIC_NOT_PROVIDED;
  }

  private void writeRejection(
      final TypedRecord<AgentInstanceRecord> command,
      final RejectionType rejectionType,
      final String reason) {
    rejectionWriter.appendRejection(command, rejectionType, reason);
    responseWriter.writeRejectedResponseOnCommand(command, rejectionType, reason);
  }

  @Override
  public SuspensionBehavior suspensionBehavior(final TypedRecord<AgentInstanceRecord> record) {
    return record.isInternalCommand() ? SuspensionBehavior.BUFFER : SuspensionBehavior.REJECT;
  }
}
