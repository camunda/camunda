/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn.behavior;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;

import io.camunda.zeebe.el.Expression;
import io.camunda.zeebe.engine.metrics.JobMetrics;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableJobWorkerElement;
import io.camunda.zeebe.engine.processing.deployment.model.element.JobWorkerProperties;
import io.camunda.zeebe.engine.processing.deployment.model.transformer.ExpressionTransformer;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.KeyGenerator;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.immutable.JobState.State;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.msgpack.value.DocumentValue;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.util.Either;
import java.util.HashMap;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class BpmnJobBehavior {

  private final JobRecord jobRecord = new JobRecord().setVariables(DocumentValue.EMPTY_DOCUMENT);
  private final HeaderEncoder headerEncoder = new HeaderEncoder();

  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedCommandWriter commandWriter;
  private final JobState jobState;
  private final ExpressionProcessor expressionBehavior;
  private final BpmnStateBehavior stateBehavior;
  private final BpmnIncidentBehavior incidentBehavior;
  private final JobMetrics jobMetrics;

  public BpmnJobBehavior(
      final KeyGenerator keyGenerator,
      final JobState jobState,
      final Writers writers,
      final ExpressionProcessor expressionBehavior,
      final BpmnStateBehavior stateBehavior,
      final BpmnIncidentBehavior incidentBehavior,
      final JobMetrics jobMetrics) {
    this.keyGenerator = keyGenerator;
    this.jobState = jobState;
    this.expressionBehavior = expressionBehavior;
    stateWriter = writers.state();
    commandWriter = writers.command();
    this.stateBehavior = stateBehavior;
    this.incidentBehavior = incidentBehavior;
    this.jobMetrics = jobMetrics;
  }

  public Either<Failure, ?> createNewJob(
      final BpmnElementContext context, final ExecutableJobWorkerElement element) {
    final var jobWorkerProperties = element.getJobWorkerProperties();
    final var scopeKey = context.getElementInstanceKey();
    return evaluateJobExpressions(jobWorkerProperties, scopeKey)
        .map(
            jobProperties -> {
              writeJobCreatedEvent(context, element, jobProperties);
              jobMetrics.jobCreated(jobProperties.getType());
              return null;
            });
  }

  private Either<Failure, JobProperties> evaluateJobExpressions(
      final JobWorkerProperties jobWorkerProps, final long scopeKey) {
    return Either.<Failure, JobProperties>right(new JobProperties())
        .flatMap(p -> evalTypeExp(jobWorkerProps, scopeKey).map(p::type))
        .flatMap(p -> evalRetriesExp(jobWorkerProps, scopeKey).map(p::retries))
        .flatMap(p -> evalAssigneeExp(jobWorkerProps, scopeKey).map(p::assignee))
        .flatMap(p -> evalCandidateGroupsExp(jobWorkerProps, scopeKey).map(p::candidateGroups));
  }

  private Either<Failure, String> evalTypeExp(
      final JobWorkerProperties jobWorkerProperties, final long scopeKey) {
    final Expression type = jobWorkerProperties.getType();
    return expressionBehavior.evaluateStringExpression(type, scopeKey);
  }

  private Either<Failure, Long> evalRetriesExp(
      final JobWorkerProperties jobWorkerProperties, final long scopeKey) {
    final Expression retries = jobWorkerProperties.getRetries();
    return expressionBehavior.evaluateLongExpression(retries, scopeKey);
  }

  private Either<Failure, String> evalAssigneeExp(
      final JobWorkerProperties jobWorkerProperties, final long scopeKey) {
    final Expression assignee = jobWorkerProperties.getAssignee();
    if (assignee == null) {
      return Either.right(null);
    }
    return expressionBehavior.evaluateStringExpression(assignee, scopeKey);
  }

  private Either<Failure, String> evalCandidateGroupsExp(
      final JobWorkerProperties jobWorkerProperties, final long scopeKey) {
    final Expression candidateGroups = jobWorkerProperties.getCandidateGroups();
    if (candidateGroups == null) {
      return Either.right(null);
    }
    return expressionBehavior
        .evaluateArrayOfStringsExpression(candidateGroups, scopeKey)
        .map(ExpressionTransformer::asListLiteral);
  }

  private void writeJobCreatedEvent(
      final BpmnElementContext context,
      final ExecutableJobWorkerElement jobWorkerElement,
      final JobProperties props) {

    final var taskHeaders = jobWorkerElement.getJobWorkerProperties().getTaskHeaders();
    final var encodedHeaders = encodeHeaders(taskHeaders, props);

    jobRecord
        .setType(props.getType())
        .setRetries(props.getRetries().intValue())
        .setCustomHeaders(encodedHeaders)
        .setBpmnProcessId(context.getBpmnProcessId())
        .setProcessDefinitionVersion(context.getProcessVersion())
        .setProcessDefinitionKey(context.getProcessDefinitionKey())
        .setProcessInstanceKey(context.getProcessInstanceKey())
        .setElementId(jobWorkerElement.getId())
        .setElementInstanceKey(context.getElementInstanceKey());

    final var jobKey = keyGenerator.nextKey();
    stateWriter.appendFollowUpEvent(jobKey, JobIntent.CREATED, jobRecord);
  }

  private DirectBuffer encodeHeaders(
      final Map<String, String> taskHeaders, final JobProperties props) {
    final var headers = new HashMap<>(taskHeaders);
    final String assignee = props.getAssignee();
    final String candidateGroups = props.getCandidateGroups();
    if (assignee != null) {
      headers.put(Protocol.USER_TASK_ASSIGNEE_HEADER_NAME, assignee);
    }
    if (candidateGroups != null) {
      headers.put(Protocol.USER_TASK_CANDIDATE_GROUPS_HEADER_NAME, candidateGroups);
    }
    return headerEncoder.encode(headers);
  }

  public void cancelJob(final BpmnElementContext context) {

    final var elementInstance = stateBehavior.getElementInstance(context);
    final long jobKey = elementInstance.getJobKey();
    if (jobKey > 0) {
      writeJobCancelCommand(jobKey);
      incidentBehavior.resolveJobIncident(jobKey);
    }
  }

  private void writeJobCancelCommand(final long jobKey) {
    final State state = jobState.getState(jobKey);

    if (state == State.ACTIVATABLE || state == State.ACTIVATED || state == State.FAILED) {
      final JobRecord job = jobState.getJob(jobKey);
      commandWriter.appendFollowUpCommand(jobKey, JobIntent.CANCEL, job);
    }
  }

  private static final class JobProperties {
    private String type;
    private Long retries;
    private String assignee;
    private String candidateGroups;

    public JobProperties type(final String type) {
      this.type = type;
      return this;
    }

    public String getType() {
      return type;
    }

    public JobProperties retries(final Long retries) {
      this.retries = retries;
      return this;
    }

    public Long getRetries() {
      return retries;
    }

    public JobProperties assignee(final String assignee) {
      this.assignee = assignee;
      return this;
    }

    public String getAssignee() {
      return assignee;
    }

    public JobProperties candidateGroups(final String candidateGroups) {
      this.candidateGroups = candidateGroups;
      return this;
    }

    public String getCandidateGroups() {
      return candidateGroups;
    }
  }

  private static final class HeaderEncoder {

    private static final int INITIAL_SIZE_KEY_VALUE_PAIR = 128;

    private final MsgPackWriter msgPackWriter = new MsgPackWriter();

    public DirectBuffer encode(final Map<String, String> taskHeaders) {
      if (taskHeaders == null || taskHeaders.isEmpty()) {
        return JobRecord.NO_HEADERS;
      }

      final MutableDirectBuffer buffer = new UnsafeBuffer(0, 0);

      final ExpandableArrayBuffer expandableBuffer =
          new ExpandableArrayBuffer(INITIAL_SIZE_KEY_VALUE_PAIR * taskHeaders.size());

      msgPackWriter.wrap(expandableBuffer, 0);
      msgPackWriter.writeMapHeader(taskHeaders.size());

      taskHeaders.forEach(
          (k, v) -> {
            if (isValidHeader(k, v)) {
              final DirectBuffer key = wrapString(k);
              msgPackWriter.writeString(key);

              final DirectBuffer value = wrapString(v);
              msgPackWriter.writeString(value);
            }
          });

      buffer.wrap(expandableBuffer.byteArray(), 0, msgPackWriter.getOffset());

      return buffer;
    }

    private boolean isValidHeader(final String key, final String value) {
      return key != null && !key.isEmpty() && value != null && !value.isEmpty();
    }
  }
}
