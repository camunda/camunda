/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.agentinstance;

import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.agenthistory.AgentHistoryRecord;
import io.camunda.zeebe.protocol.impl.record.value.agentinstance.AgentInstanceMetrics;
import io.camunda.zeebe.protocol.impl.record.value.agentinstance.AgentInstanceRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.value.AgentHistoryContentType;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRecordValue;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRecordValue.AgentHistoryMessageContentValue;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRole;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Shared logic for the {@code AGENT_HISTORY}/{@code AGENT_INSTANCE} processors that deal with an
 * embedded {@code history[]} batch: validating the job a batch is attributed to, validating the
 * shape of the batch itself, and applying it to an {@code AgentInstanceRecord}.
 */
public final class AgentHistoryBatchHelper {

  /** The names of the {@code AgentInstanceRecord} attributes a history item can affect. */
  static final Set<String> ALLOWED_CONFIGURATION_ATTRIBUTES =
      Set.of(
          AgentInstanceRecord.ATTR_MODEL,
          AgentInstanceRecord.ATTR_PROVIDER,
          AgentInstanceRecord.ATTR_SYSTEM_PROMPT,
          AgentInstanceRecord.ATTR_TOOLS,
          AgentInstanceRecord.ATTR_MAX_TOKENS,
          AgentInstanceRecord.ATTR_MAX_MODEL_CALLS,
          AgentInstanceRecord.ATTR_MAX_TOOL_CALLS);

  static final String ERROR_MSG_HISTORY_ITEM_ID_MISSING =
      "Expected to process history item at index %d, but historyItemId is missing (got empty "
          + "string). Every history item must have a non-empty historyItemId.";
  static final String ERROR_MSG_ROLE_UNSPECIFIED =
      "Expected to process history item with historyItemId '%s', but its role is UNSPECIFIED. "
          + "Every history item must declare a role.";
  static final String ERROR_MSG_LOOP_ITERATION_MISSING =
      "Expected to process history item with historyItemId '%s', but loopIteration is missing "
          + "(got %d). Every history item must declare a positive loopIteration.";
  static final String ERROR_MSG_JOB_NOT_ACTIVE =
      "Expected job with key '%d' to be active, but it was not.";
  static final String ERROR_MSG_JOB_LEASE_MISMATCH =
      "Expected job with key '%d' to hold the supplied lease, but it did not match. The job may "
          + "have been re-activated.";
  static final String ERROR_MSG_JOB_ELEMENT_MISMATCH =
      "Expected job '%d' to be associated with element instance '%d', but it is associated with element instance '%d'";
  static final String ERROR_MSG_JOB_REQUIRED_FOR_HISTORY =
      "Expected a job to be provided for the embedded history batch, but no jobKey was set."
          + " A history batch must be attributed to the active job that produced it.";
  private static final String ERROR_MSG_UNKNOWN_ATTRIBUTES =
      "Expected to update agent instance configuration with history item '%s',"
          + " but changedAttributes contained unknown attribute(s) %s. Allowed attributes are: %s.";

  private final KeyGenerator keyGenerator;
  private final ProcessingState processingState;

  public AgentHistoryBatchHelper(
      final KeyGenerator keyGenerator, final ProcessingState processingState) {
    this.keyGenerator = keyGenerator;
    this.processingState = processingState;
  }

  /**
   * Validates the job context a command carries. If a history batch is present, {@code jobKey}
   * becomes required — a batch must always be attributed to the job that produced it — otherwise no
   * job at all remains valid. When a job is supplied (with or without a batch), it must refer to a
   * currently-active job, that job's lease token (if any) must match {@code jobLease}, and the job
   * must belong to {@code elementInstanceKey}.
   *
   * @return the active {@link JobRecord} if a job was supplied and is valid, {@code null} wrapped
   *     in {@link Either#right} if no job was supplied and none was required, otherwise the {@link
   *     Rejection} to surface
   */
  public Either<Rejection, JobRecord> validateJobContext(
      final long jobKey,
      final String jobLease,
      final long elementInstanceKey,
      final List<? extends AgentHistoryRecordValue> history) {

    if (jobKey == -1L) {
      if (history != null && !history.isEmpty()) {
        return Either.left(
            new Rejection(RejectionType.INVALID_ARGUMENT, ERROR_MSG_JOB_REQUIRED_FOR_HISTORY));
      } else {
        return Either.right(null);
      }
    }

    final var jobState = processingState.getJobState();
    if (jobState.getState(jobKey) != JobState.State.ACTIVATED) {
      return Either.left(
          new Rejection(RejectionType.NOT_FOUND, ERROR_MSG_JOB_NOT_ACTIVE.formatted(jobKey)));
    }

    final var job = jobState.getJob(jobKey);
    if (job.hasLeaseToken() && !Objects.equals(jobLease, job.getLeaseToken())) {
      return Either.left(
          new Rejection(RejectionType.NOT_FOUND, ERROR_MSG_JOB_LEASE_MISMATCH.formatted(jobKey)));
    }

    final var jobElementInstanceKey = job.getElementInstanceKey();
    if (jobElementInstanceKey != elementInstanceKey) {
      return Either.left(
          new Rejection(
              RejectionType.INVALID_ARGUMENT,
              ERROR_MSG_JOB_ELEMENT_MISMATCH.formatted(
                  jobKey, elementInstanceKey, jobElementInstanceKey)));
    }

    return Either.right(job);
  }

  /**
   * Validates every item in the batch, in order: {@code historyItemId} must be non-empty, {@code
   * role} must not be {@code UNSPECIFIED}, {@code loopIteration} must be a positive integer (the
   * {@code 0} default means the field was left unset), and — for {@link
   * AgentHistoryRole#CONFIGURATION} items only — every name in {@code changedAttributes} must be
   * one this helper actually knows how to apply.
   *
   * @return the rejection for the first invalid item found, or {@link Either#rightVoid()} if the
   *     whole batch is valid
   */
  public Either<Rejection, Void> validateHistory(
      final List<? extends AgentHistoryRecordValue> history) {
    if (history == null || history.isEmpty()) {
      return Either.rightVoid();
    }

    for (int i = 0; i < history.size(); i++) {
      final var item = history.get(i);
      final var historyItemId = item.getHistoryItemId();

      if (historyItemId.isEmpty()) {
        return Either.left(
            new Rejection(
                RejectionType.INVALID_ARGUMENT, ERROR_MSG_HISTORY_ITEM_ID_MISSING.formatted(i)));
      }

      if (item.getRole() == AgentHistoryRole.UNSPECIFIED) {
        return Either.left(
            new Rejection(
                RejectionType.INVALID_ARGUMENT,
                ERROR_MSG_ROLE_UNSPECIFIED.formatted(historyItemId)));
      }

      if (item.getLoopIteration() < 1) {
        return Either.left(
            new Rejection(
                RejectionType.INVALID_ARGUMENT,
                ERROR_MSG_LOOP_ITERATION_MISSING.formatted(
                    historyItemId, item.getLoopIteration())));
      }

      if (item.getRole() == AgentHistoryRole.CONFIGURATION) {
        final var unknown =
            item.getChangedAttributes().stream()
                .filter(Predicate.not(ALLOWED_CONFIGURATION_ATTRIBUTES::contains))
                .toList();
        if (!unknown.isEmpty()) {
          return Either.left(
              new Rejection(
                  RejectionType.INVALID_ARGUMENT,
                  ERROR_MSG_UNKNOWN_ATTRIBUTES.formatted(
                      historyItemId, unknown, ALLOWED_CONFIGURATION_ATTRIBUTES)));
        }
      }
    }
    return Either.rightVoid();
  }

  /**
   * Applies an already-validated batch onto {@code target}, in array order: builds one {@code
   * AGENT_HISTORY} event per item (a full copy of the item, with its record-context fields
   * overwritten to match {@code target}/{@code jobKey}/{@code jobLease}), accumulates metrics
   * immediately, and — for {@link AgentHistoryRole#CONFIGURATION} items only — applies whichever of
   * model/provider/systemPrompt/tools/limits the item names in its own {@code changedAttributes}.
   *
   * <p><strong>Mutates {@code target} in place</strong> (metrics, definition, tools, limits,
   * history) and does not itself emit any event — the caller is responsible for turning {@code
   * target.getHistory()} into {@code AGENT_HISTORY:CREATED} follow-up events.
   *
   * @return the {@code AgentInstanceRecord} attribute names that actually changed as a result
   */
  Set<String> apply(
      final AgentInstanceRecord target,
      final long jobKey,
      final String jobLease,
      final long elementInstanceKey,
      final List<? extends AgentHistoryRecordValue> history) {
    final var changedAttributes = new HashSet<String>();
    final var items = new ArrayList<AgentHistoryRecord>(history.size());

    for (final var item : history) {
      final var historyKey = keyGenerator.nextKey();

      final var event = new AgentHistoryRecord();
      // `item` is always a concrete AgentHistoryRecord at runtime (the only implementation of
      // AgentHistoryRecordValue) — copyFrom() needs the concrete type since it round-trips
      // through BufferWriter/BufferReader, which the protocol interface doesn't expose.
      event.copyFrom((AgentHistoryRecord) item);
      event
          .setAgentHistoryKey(historyKey)
          .setAgentInstanceKey(target.getAgentInstanceKey())
          .setElementInstanceKey(elementInstanceKey)
          .setProcessInstanceKey(target.getProcessInstanceKey())
          .setRootProcessInstanceKey(target.getRootProcessInstanceKey())
          .setBpmnProcessId(target.getBpmnProcessId())
          .setProcessDefinitionKey(target.getProcessDefinitionKey())
          .setTenantId(target.getTenantId())
          .setJobKey(jobKey)
          .setJobLease(jobLease);

      if (applyMetrics(target.getMetrics(), item)) {
        changedAttributes.add(AgentInstanceRecord.ATTR_METRICS);
      }
      changedAttributes.addAll(applyConfigurationChanges(target, item));

      items.add(event);
    }

    target.setHistory(items);
    return changedAttributes;
  }

  /**
   * For a {@link AgentHistoryRole#CONFIGURATION} item, applies whichever of
   * model/provider/systemPrompt/tools/limits the item's own {@code changedAttributes} names,
   * immediately onto {@code target}'s live definition/tools/limits. Items of any other role never
   * affect these fields.
   *
   * @return the {@code AgentInstanceRecord} attribute names that actually changed
   */
  private Set<String> applyConfigurationChanges(
      final AgentInstanceRecord target, final AgentHistoryRecordValue item) {
    if (item.getRole() != AgentHistoryRole.CONFIGURATION) {
      return Set.of();
    }

    final var changed = new HashSet<String>();
    // note: this is resolved once to avoid deserializing every time
    final var itemChangedAttributes = item.getChangedAttributes();

    if (itemChangedAttributes.contains(AgentInstanceRecord.ATTR_MODEL)) {
      target.getDefinition().setModel(item.getModel());
      changed.add(AgentInstanceRecord.ATTR_MODEL);
    }
    if (itemChangedAttributes.contains(AgentInstanceRecord.ATTR_PROVIDER)) {
      target.getDefinition().setProvider(item.getProvider());
      changed.add(AgentInstanceRecord.ATTR_PROVIDER);
    }
    if (itemChangedAttributes.contains(AgentInstanceRecord.ATTR_SYSTEM_PROMPT)) {
      target.getDefinition().setSystemPrompt(extractText(item.getSystemPrompt()));
      changed.add(AgentInstanceRecord.ATTR_SYSTEM_PROMPT);
    }
    if (itemChangedAttributes.contains(AgentInstanceRecord.ATTR_TOOLS)) {
      target.setTools(item.getTools());
      changed.add(AgentInstanceRecord.ATTR_TOOLS);
    }
    if (itemChangedAttributes.contains(AgentInstanceRecord.ATTR_MAX_TOKENS)) {
      target.getLimits().setMaxTokens(item.getLimits().getMaxTokens());
      changed.add(AgentInstanceRecord.ATTR_MAX_TOKENS);
    }
    if (itemChangedAttributes.contains(AgentInstanceRecord.ATTR_MAX_MODEL_CALLS)) {
      target.getLimits().setMaxModelCalls(item.getLimits().getMaxModelCalls());
      changed.add(AgentInstanceRecord.ATTR_MAX_MODEL_CALLS);
    }
    if (itemChangedAttributes.contains(AgentInstanceRecord.ATTR_MAX_TOOL_CALLS)) {
      target.getLimits().setMaxToolCalls(item.getLimits().getMaxToolCalls());
      changed.add(AgentInstanceRecord.ATTR_MAX_TOOL_CALLS);
    }

    return changed;
  }

  /**
   * Concatenates every {@code TEXT} content block's text (joined with a blank line) — the dedicated
   * {@code systemPrompt} content-block list uses the same block shape as {@code content}, but only
   * {@code TEXT} blocks are meaningful as a system prompt.
   *
   * <p><strong>Workaround:</strong> {@code AgentInstanceRecord}'s {@code systemPrompt} field is a
   * plain string, so a multi-block system prompt (e.g. text interleaved with documents) is
   * flattened and lossy here — only the {@code TEXT} blocks survive. #58797 is expected to change
   * how the agent instance stores its system prompt to allow saving the full multi-content value;
   * this flattening should be removed once that lands.
   */
  private String extractText(final List<? extends AgentHistoryMessageContentValue> blocks) {
    return blocks.stream()
        .filter(block -> block.getContentType() == AgentHistoryContentType.TEXT)
        .map(AgentHistoryMessageContentValue::getText)
        .collect(Collectors.joining("\n\n"));
  }

  /**
   * Sums each positive field of {@code item}'s metrics onto {@code current}, skipping non-positive
   * fields (covers the {@code -1} not-provided sentinel and {@code 0} no-change). {@code
   * modelCalls}/{@code toolCalls} aren't part of the item's metrics — they're derived instead:
   * every {@code ASSISTANT} item represents exactly one model call, and its own {@code toolCalls}
   * count is the number of tool calls it dispatched.
   *
   * @return whether any field of {@code current} actually changed
   */
  private boolean applyMetrics(
      final AgentInstanceMetrics current, final AgentHistoryRecordValue item) {
    boolean changed = false;
    final var delta = item.getMetrics();
    if (delta.getInputTokens() > 0) {
      current.setInputTokens(current.getInputTokens() + delta.getInputTokens());
      changed = true;
    }
    if (delta.getOutputTokens() > 0) {
      current.setOutputTokens(current.getOutputTokens() + delta.getOutputTokens());
      changed = true;
    }
    if (delta.getReasoningTokenCount() > 0) {
      current.setReasoningTokenCount(
          current.getReasoningTokenCount() + delta.getReasoningTokenCount());
      changed = true;
    }
    if (delta.getCacheCreationTokenCount() > 0) {
      current.setCacheCreationTokenCount(
          current.getCacheCreationTokenCount() + delta.getCacheCreationTokenCount());
      changed = true;
    }
    if (delta.getCacheReadTokenCount() > 0) {
      current.setCacheReadTokenCount(
          current.getCacheReadTokenCount() + delta.getCacheReadTokenCount());
      changed = true;
    }
    if (item.getRole() == AgentHistoryRole.ASSISTANT) {
      current.setModelCalls(current.getModelCalls() + 1);
      current.setToolCalls(current.getToolCalls() + item.getToolCalls().size());
      changed = true;
    }
    return changed;
  }
}
