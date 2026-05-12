/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import io.camunda.zeebe.engine.processing.secret.SecretStore;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.UserTaskState;
import io.camunda.zeebe.engine.state.immutable.VariableState;
import io.camunda.zeebe.msgpack.value.DocumentValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;

public class JobVariablesCollector {

  private final VariableState variableState;
  private final UserTaskState userTaskState;
  private final ElementInstanceState elementInstanceState;
  private final SecretStore secretStore;

  public JobVariablesCollector(
      final ProcessingState processingState, final SecretStore secretStore) {
    variableState = processingState.getVariableState();
    userTaskState = processingState.getUserTaskState();
    elementInstanceState = processingState.getElementInstanceState();
    this.secretStore = secretStore;
  }

  /**
   * Populates {@code jobRecord}'s {@code variables} payload from the variable store and — if the
   * worker explicitly named any {@code camunda.secret.X} entries in {@code requestedVariables} —
   * from the configured {@link SecretStore}. Secret resolution happens last and is merged into the
   * outgoing document under the full reference key (e.g. {@code camunda.secret.MY_SECRET}),
   * matching how the worker asked for it.
   *
   * <p>If any requested secret is absent from the store, the method returns the offending reference
   * name (the first such miss) and leaves {@code jobRecord} untouched, so the caller can raise an
   * incident on this job without leaking through a partially-resolved payload.
   *
   * @return {@link Optional#empty()} on success; otherwise the missing reference name (e.g. {@code
   *     "camunda.secret.MY_SECRET"})
   */
  public Optional<String> setJobVariables(
      final Collection<DirectBuffer> requestedVariables, final JobRecord jobRecord) {
    // Resolve secret references first so a missing secret short-circuits the variable read.
    final var split = splitVariables(requestedVariables);
    if (split.missingSecret() != null) {
      return Optional.of(split.missingSecret());
    }

    final boolean fetchAll = requestedVariables.isEmpty();
    final DirectBuffer processVariables = readProcessVariables(jobRecord, split, fetchAll);

    final DirectBuffer jobVariables =
        switch (jobRecord.getJobKind()) {
          case BPMN_ELEMENT, EXECUTION_LISTENER, AD_HOC_SUB_PROCESS ->
              mergeSecrets(processVariables, split.resolvedSecrets());
          case TASK_LISTENER -> {
            final var taskVariablesMap =
                getTaskVariables(split.regularVariables(), jobRecord.getElementInstanceKey());
            final Map<String, Object> merged = MsgPackConverter.convertToMap(processVariables);
            merged.putAll(taskVariablesMap);
            merged.putAll(split.resolvedSecrets());
            yield BufferUtil.wrapArray(MsgPackConverter.convertToMsgPack(merged));
          }
        };

    jobRecord.setVariables(jobVariables);
    return Optional.empty();
  }

  private DirectBuffer readProcessVariables(
      final JobRecord jobRecord, final SplitVariables split, final boolean fetchAll) {
    final long elementInstanceKey = jobRecord.getElementInstanceKey();
    if (elementInstanceKey < 0) {
      return DocumentValue.EMPTY_DOCUMENT;
    }
    if (fetchAll) {
      // No name filter — the worker wants everything; secret namespace is never persisted as a
      // process variable, so we don't need to merge anything else here.
      return variableState.getVariablesAsDocument(elementInstanceKey);
    }
    if (split.regularVariables().isEmpty()) {
      // Worker asked exclusively for secrets — skip the state read entirely.
      return DocumentValue.EMPTY_DOCUMENT;
    }
    return variableState.getVariablesAsDocument(elementInstanceKey, split.regularVariables());
  }

  private static DirectBuffer mergeSecrets(
      final DirectBuffer document, final Map<String, String> secrets) {
    if (secrets.isEmpty()) {
      return document;
    }
    final Map<String, Object> merged = MsgPackConverter.convertToMap(document);
    merged.putAll(secrets);
    return BufferUtil.wrapArray(MsgPackConverter.convertToMsgPack(merged));
  }

  private SplitVariables splitVariables(final Collection<DirectBuffer> requestedVariables) {
    final Map<String, String> resolvedSecrets = new HashMap<>();
    final List<DirectBuffer> regularVariables = new ArrayList<>(requestedVariables.size());
    for (final DirectBuffer name : requestedVariables) {
      final String nameStr = BufferUtil.bufferAsString(name);
      if (nameStr.startsWith(SecretStore.FEEL_NAMESPACE)) {
        final String secretName = nameStr.substring(SecretStore.FEEL_NAMESPACE.length());
        final Optional<String> resolved = secretStore.resolve(secretName);
        if (resolved.isEmpty()) {
          return SplitVariables.miss(nameStr);
        }
        resolvedSecrets.put(nameStr, resolved.get());
      } else {
        regularVariables.add(name);
      }
    }
    return new SplitVariables(regularVariables, resolvedSecrets, null);
  }

  private Map<String, Object> getTaskVariables(
      final Collection<DirectBuffer> requestedVariables, final long elementInstanceKey) {
    final var elementInstance = elementInstanceState.getInstance(elementInstanceKey);
    if (elementInstance == null) {
      return Map.of();
    }
    final var userTaskIntermediateState =
        userTaskState.getIntermediateState(elementInstance.getUserTaskKey());
    if (userTaskIntermediateState == null) {
      return Map.of();
    }
    final var taskVariables = userTaskIntermediateState.getRecord().getVariablesBuffer();
    if (taskVariables.capacity() <= 0) {
      return Map.of();
    }
    final Map<String, Object> taskVariablesMap = MsgPackConverter.convertToMap(taskVariables);
    if (requestedVariables.isEmpty()) {
      return taskVariablesMap;
    }
    return taskVariablesMap.entrySet().stream()
        .filter(e -> requestedVariables.contains(BufferUtil.wrapString(e.getKey())))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  /**
   * Result of splitting the worker's {@code fetchVariables} list into regular variables and
   * resolved secret references. If {@link #missingSecret} is non-null, resolution short-circuited
   * on that reference and the other fields are not meaningful.
   */
  private record SplitVariables(
      List<DirectBuffer> regularVariables,
      Map<String, String> resolvedSecrets,
      String missingSecret) {
    static SplitVariables miss(final String missingSecret) {
      return new SplitVariables(List.of(), Map.of(), missingSecret);
    }
  }
}
