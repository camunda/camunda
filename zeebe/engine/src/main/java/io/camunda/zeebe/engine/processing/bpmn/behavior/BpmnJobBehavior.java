/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.behavior;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Function;
import com.google.common.base.Strings;
import io.camunda.zeebe.el.Expression;
import io.camunda.zeebe.engine.metrics.JobMetrics;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableJobWorkerElement;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutionListener;
import io.camunda.zeebe.engine.processing.deployment.model.element.JobWorkerProperties;
import io.camunda.zeebe.engine.processing.deployment.model.element.LinkedResource;
import io.camunda.zeebe.engine.processing.deployment.model.element.TaskListener;
import io.camunda.zeebe.engine.processing.deployment.model.transformer.ExpressionTransformer;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.immutable.JobState.State;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeExecutionListenerEventType;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskListenerEventType;
import io.camunda.zeebe.msgpack.value.DocumentValue;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.protocol.record.value.JobListenerEventType;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.Either;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BpmnJobBehavior {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(BpmnJobBehavior.class.getPackageName());
  private static final Set<State> CANCELABLE_STATES =
      EnumSet.of(State.ACTIVATABLE, State.ACTIVATED, State.FAILED, State.ERROR_THROWN);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final JobRecord jobRecord = new JobRecord().setVariables(DocumentValue.EMPTY_DOCUMENT);
  private final HeaderEncoder headerEncoder = new HeaderEncoder(LOGGER);
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final JobState jobState;
  private final ExpressionProcessor expressionBehavior;
  private final BpmnStateBehavior stateBehavior;
  private final BpmnIncidentBehavior incidentBehavior;
  private final JobMetrics jobMetrics;
  private final BpmnJobActivationBehavior jobActivationBehavior;
  private final BpmnUserTaskBehavior userTaskBehavior;

  public BpmnJobBehavior(
      final KeyGenerator keyGenerator,
      final JobState jobState,
      final Writers writers,
      final ExpressionProcessor expressionBehavior,
      final BpmnStateBehavior stateBehavior,
      final BpmnIncidentBehavior incidentBehavior,
      final BpmnJobActivationBehavior jobActivationBehavior,
      final JobMetrics jobMetrics,
      final BpmnUserTaskBehavior userTaskBehavior) {
    this.keyGenerator = keyGenerator;
    this.jobState = jobState;
    this.expressionBehavior = expressionBehavior;
    stateWriter = writers.state();
    this.stateBehavior = stateBehavior;
    this.incidentBehavior = incidentBehavior;
    this.jobMetrics = jobMetrics;
    this.jobActivationBehavior = jobActivationBehavior;
    this.userTaskBehavior = userTaskBehavior;
  }

  public Either<Failure, JobProperties> evaluateJobExpressions(
      final JobWorkerProperties jobWorkerProps, final BpmnElementContext context) {
    final var scopeKey = context.getElementInstanceKey();
    return Either.<Failure, JobProperties>right(new JobProperties())
        .flatMap(p -> evalTypeExp(jobWorkerProps.getType(), scopeKey).map(p::type))
        .flatMap(p -> evalRetriesExp(jobWorkerProps.getRetries(), scopeKey).map(p::retries))
        .flatMap(p -> evalLinkedResourceKey(jobWorkerProps).map(p::resourceKey))
        .flatMap(
            p ->
                evalLinkedResourceProperty(jobWorkerProps, LinkedResource::getResourceId)
                    .map(p::resourceId))
        .flatMap(
            p ->
                evalLinkedResourceProperty(jobWorkerProps, LinkedResource::getResourceType)
                    .map(p::resourceType))
        .flatMap(
            p ->
                evalLinkedResourceProperty(jobWorkerProps, LinkedResource::getLinkName)
                    .map(p::linkName))
        .flatMap(
            p ->
                userTaskBehavior
                    .evaluateAssigneeExpression(jobWorkerProps.getAssignee(), scopeKey)
                    .map(p::assignee))
        .flatMap(
            p ->
                userTaskBehavior
                    .evaluateCandidateGroupsExpression(
                        jobWorkerProps.getCandidateGroups(), scopeKey)
                    .map(BpmnJobBehavior::asListLiteralOrNull)
                    .map(p::candidateGroups))
        .flatMap(
            p ->
                userTaskBehavior
                    .evaluateCandidateUsersExpression(jobWorkerProps.getCandidateUsers(), scopeKey)
                    .map(BpmnJobBehavior::asListLiteralOrNull)
                    .map(p::candidateUsers))
        .flatMap(
            p ->
                userTaskBehavior
                    .evaluateDateExpression(jobWorkerProps.getDueDate(), scopeKey)
                    .map(p::dueDate))
        .flatMap(
            p ->
                userTaskBehavior
                    .evaluateDateExpression(jobWorkerProps.getFollowUpDate(), scopeKey)
                    .map(p::followUpDate))
        .flatMap(
            p ->
                userTaskBehavior
                    .evaluateFormIdExpressionToFormKey(
                        jobWorkerProps.getFormId(),
                        jobWorkerProps.getFormBindingType(),
                        jobWorkerProps.getFormVersionTag(),
                        context,
                        scopeKey)
                    .map(key -> Objects.toString(key, null))
                    .map(p::formKey));
  }

  private Either<Failure, String> evalLinkedResourceProperty(
      final JobWorkerProperties props, final Function<LinkedResource, String> propertyFunction) {
    final List<LinkedResource> linkedResources = props.getLinkedResources();
    if (linkedResources == null || linkedResources.isEmpty()) {
      return Either.right(null);
    }
    return Either.right(
        linkedResources.stream()
            .map(propertyFunction)
            .collect(Collectors.joining(PROPERTY_DELIMITER)));
  }

  private Either<Failure, String> evalLinkedResourceKey(final JobWorkerProperties props) {
    final List<LinkedResource> linkedResources = props.getLinkedResources();
    if (linkedResources == null || linkedResources.isEmpty()) {
      return Either.right(null);
    }
    return Either.right(
        linkedResources.stream()
            .map(l -> resolveLinkedResourceKey(l))
            .collect(Collectors.joining(PROPERTY_DELIMITER)));
  }

  private String resolveLinkedResourceKey(final LinkedResource linkedResource) {
    // TODO Add implementation in the next PR
    return "";
  }

  private static String asListLiteralOrNull(final List<String> list) {
    return list == null ? null : ExpressionTransformer.asListLiteral(list);
  }

  private static String asNotEmptyListLiteralOrNull(final List<String> list) {
    return list == null || list.isEmpty() ? null : ExpressionTransformer.asListLiteral(list);
  }

  private static String notBlankOrNull(final String input) {
    return StringUtils.isBlank(input) ? null : input;
  }

  public void createNewJob(
      final BpmnElementContext context,
      final ExecutableJobWorkerElement element,
      final JobProperties jobProperties) {

    writeJobCreatedEvent(
        context,
        jobProperties,
        JobKind.BPMN_ELEMENT,
        JobListenerEventType.UNSPECIFIED,
        element.getJobWorkerProperties().getTaskHeaders());
  }

  public void createNewExecutionListenerJob(
      final BpmnElementContext context,
      final JobProperties jobProperties,
      final ExecutionListener executionListener) {

    final var jobListenerEventType =
        fromExecutionListenerEventType(executionListener.getEventType());
    writeJobCreatedEvent(
        context,
        jobProperties,
        JobKind.EXECUTION_LISTENER,
        jobListenerEventType,
        Collections.emptyMap());
  }

  public void createNewTaskListenerJob(
      final BpmnElementContext context,
      final UserTaskRecord taskRecordValue,
      final TaskListener listener) {
    evaluateTaskListenerJobExpressions(listener.getJobWorkerProperties(), context, taskRecordValue)
        .thenDo(
            listenerJobProperties ->
                writeJobCreatedEvent(
                    context,
                    listenerJobProperties,
                    JobKind.TASK_LISTENER,
                    fromTaskListenerEventType(listener.getEventType()),
                    extractUserTaskHeaders(taskRecordValue)))
        .ifLeft(failure -> incidentBehavior.createIncident(failure, context));
  }

  private Either<Failure, JobProperties> evaluateTaskListenerJobExpressions(
      final JobWorkerProperties jobWorkerProps,
      final BpmnElementContext context,
      final UserTaskRecord taskRecordValue) {
    final var scopeKey = context.getElementInstanceKey();
    return Either.<Failure, JobProperties>right(new JobProperties())
        // Evaluate and set basic job properties
        .flatMap(p -> evalTypeExp(jobWorkerProps.getType(), scopeKey).map(p::type))
        .flatMap(p -> evalRetriesExp(jobWorkerProps.getRetries(), scopeKey).map(p::retries))
        // Handle user task-related properties
        .map(
            p ->
                Optional.of(taskRecordValue.getFormKey())
                    .filter(formKey -> formKey > 0)
                    .map(Objects::toString)
                    .map(p::formKey)
                    .orElse(p))
        .map(
            p ->
                Optional.of(taskRecordValue.getAssignee())
                    .map(BpmnJobBehavior::notBlankOrNull)
                    .map(p::assignee)
                    .orElse(p))
        .map(
            p ->
                Optional.of(taskRecordValue.getCandidateGroupsList())
                    .map(BpmnJobBehavior::asNotEmptyListLiteralOrNull)
                    .map(p::candidateGroups)
                    .orElse(p))
        .map(
            p ->
                Optional.of(taskRecordValue.getCandidateUsersList())
                    .map(BpmnJobBehavior::asNotEmptyListLiteralOrNull)
                    .map(p::candidateUsers)
                    .orElse(p))
        .map(
            p ->
                Optional.of(taskRecordValue.getDueDate())
                    .map(BpmnJobBehavior::notBlankOrNull)
                    .map(p::dueDate)
                    .orElse(p))
        .map(
            p ->
                Optional.of(taskRecordValue.getFollowUpDate())
                    .map(BpmnJobBehavior::notBlankOrNull)
                    .map(p::followUpDate)
                    .orElse(p));
  }

  private static JobListenerEventType fromExecutionListenerEventType(
      final ZeebeExecutionListenerEventType eventType) {
    return switch (eventType) {
      case start -> JobListenerEventType.START;
      case end -> JobListenerEventType.END;
    };
  }

  private static JobListenerEventType fromTaskListenerEventType(
      final ZeebeTaskListenerEventType eventType) {
    return switch (eventType) {
      case assigning -> JobListenerEventType.ASSIGNING;
      case updating -> JobListenerEventType.UPDATING;
      case completing -> JobListenerEventType.COMPLETING;
      default ->
          throw new IllegalStateException("Unexpected ZeebeTaskListenerEventType: " + eventType);
    };
  }

  private Either<Failure, String> evalTypeExp(final Expression type, final long scopeKey) {
    return expressionBehavior
        .evaluateStringExpression(type, scopeKey)
        .flatMap(
            result ->
                Strings.isNullOrEmpty(result)
                    ? Either.left(
                        new Failure(
                            String.format(
                                "Expected result of the expression '%s' to be a not-empty string, but was an empty string.",
                                type.getExpression()),
                            ErrorType.EXTRACT_VALUE_ERROR,
                            scopeKey))
                    : Either.right(result));
  }

  private Either<Failure, Long> evalRetriesExp(final Expression retries, final long scopeKey) {
    return expressionBehavior.evaluateLongExpression(retries, scopeKey);
  }

  private void writeJobCreatedEvent(
      final BpmnElementContext context,
      final JobProperties props,
      final JobKind jobKind,
      final JobListenerEventType jobListenerEventType,
      final Map<String, String> taskHeaders) {

    final var encodedHeaders = encodeHeaders(taskHeaders, props);

    jobRecord
        .setType(props.getType())
        .setJobKind(jobKind)
        .setListenerEventType(jobListenerEventType)
        .setRetries(props.getRetries().intValue())
        .setCustomHeaders(encodedHeaders)
        .setBpmnProcessId(context.getBpmnProcessId())
        .setProcessDefinitionVersion(context.getProcessVersion())
        .setProcessDefinitionKey(context.getProcessDefinitionKey())
        .setProcessInstanceKey(context.getProcessInstanceKey())
        .setElementId(context.getElementId())
        .setElementInstanceKey(context.getElementInstanceKey())
        .setTenantId(context.getTenantId());

    final var jobKey = keyGenerator.nextKey();
    stateWriter.appendFollowUpEvent(jobKey, JobIntent.CREATED, jobRecord);
    jobActivationBehavior.publishWork(jobKey, jobRecord);
    jobMetrics.jobCreated(props.getType(), jobKind);
  }

  private DirectBuffer encodeHeaders(
      final Map<String, String> taskHeaders, final JobProperties props) {
    final var headers = new HashMap<>(taskHeaders);
    final String assignee = props.getAssignee();
    final String candidateGroups = props.getCandidateGroups();
    final String candidateUsers = props.getCandidateUsers();
    final String dueDate = props.getDueDate();
    final String followUpDate = props.getFollowUpDate();
    final String formKey = props.getFormKey();
    final List<LinkedResource> linkedResources = props.getLinkedResources();

    if (assignee != null && !assignee.isEmpty()) {
      headers.put(Protocol.USER_TASK_ASSIGNEE_HEADER_NAME, assignee);
    }
    if (candidateGroups != null && !candidateGroups.isEmpty()) {
      headers.put(Protocol.USER_TASK_CANDIDATE_GROUPS_HEADER_NAME, candidateGroups);
    }
    if (candidateUsers != null && !candidateUsers.isEmpty()) {
      headers.put(Protocol.USER_TASK_CANDIDATE_USERS_HEADER_NAME, candidateUsers);
    }
    if (dueDate != null && !dueDate.isEmpty()) {
      headers.put(Protocol.USER_TASK_DUE_DATE_HEADER_NAME, dueDate);
    }
    if (followUpDate != null && !followUpDate.isEmpty()) {
      headers.put(Protocol.USER_TASK_FOLLOW_UP_DATE_HEADER_NAME, followUpDate);
    }
    if (formKey != null && !formKey.isEmpty()) {
      headers.put(Protocol.USER_TASK_FORM_KEY_HEADER_NAME, formKey);
    }
    if (linkedResources != null && !linkedResources.isEmpty()) {
      try {
        final String linkedResourcesJson = OBJECT_MAPPER.writeValueAsString(linkedResources);
        headers.put(Protocol.LINKED_RESOURCES_HEADER_NAME, linkedResourcesJson);
      } catch (final JsonProcessingException e) {
        throw new IllegalArgumentException(
            "Failed to convert linked resource headers to json object", e);
      }
    }
    return headerEncoder.encode(headers);
  }

  private Map<String, String> extractUserTaskHeaders(final UserTaskRecord userTaskRecord) {
    final var headers = new HashMap<String, String>();

    if (userTaskRecord.getUserTaskKey() > 0) {
      headers.put(
          Protocol.USER_TASK_KEY_HEADER_NAME, String.valueOf(userTaskRecord.getUserTaskKey()));
    }

    if (userTaskRecord.getPriority() > 0) {
      headers.put(
          Protocol.USER_TASK_PRIORITY_HEADER_NAME, String.valueOf(userTaskRecord.getPriority()));
    }

    if (StringUtils.isNotEmpty(userTaskRecord.getAction())) {
      headers.put(Protocol.USER_TASK_ACTION_HEADER_NAME, userTaskRecord.getAction());
    }

    return Collections.unmodifiableMap(headers);
  }

  public void cancelJob(final BpmnElementContext context) {
    final var elementInstance = stateBehavior.getElementInstance(context);
    cancelJob(elementInstance);
  }

  public void cancelJob(final ElementInstance elementInstance) {
    final long jobKey = elementInstance.getJobKey();
    if (jobKey > 0) {
      writeJobCanceled(jobKey);
      incidentBehavior.resolveJobIncident(jobKey);
    }
  }

  private void writeJobCanceled(final long jobKey) {
    final State state = jobState.getState(jobKey);

    if (CANCELABLE_STATES.contains(state)) {
      final JobRecord job = jobState.getJob(jobKey);
      // Note that this logic is duplicated in JobCancelProcessor, if you change this please change
      // it there as well.
      stateWriter.appendFollowUpEvent(jobKey, JobIntent.CANCELED, job);
      jobMetrics.jobCanceled(job.getType(), job.getJobKind());
    }
  }

  public static final class JobProperties {
    private String type;
    private Long retries;
    private String assignee;
    private String candidateGroups;
    private String candidateUsers;
    private String dueDate;
    private String followUpDate;
    private String formKey;
    private List<LinkedResource> linkedResources;

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

    public JobProperties candidateUsers(final String candidateUsers) {
      this.candidateUsers = candidateUsers;
      return this;
    }

    public String getCandidateUsers() {
      return candidateUsers;
    }

    public JobProperties dueDate(final String dueDate) {
      this.dueDate = dueDate;
      return this;
    }

    public String getDueDate() {
      return dueDate;
    }

    public JobProperties followUpDate(final String followUpDate) {
      this.followUpDate = followUpDate;
      return this;
    }

    public String getFollowUpDate() {
      return followUpDate;
    }

    public JobProperties formKey(final String formId) {
      formKey = formId;
      return this;
    }

    public String getFormKey() {
      return formKey;
    }

    public JobProperties linkedResources(final List<LinkedResource> linkedResources) {
      this.linkedResources = linkedResources;
      return this;
    }

    public List<LinkedResource> getLinkedResources() {
      return linkedResources;
    }
  }

  public static final class LinkedResource {
    private String resourceKey;
    private String resourceType;
    private String linkName;

    public String getResourceKey() {
      return resourceKey;
    }

    public void setResourceKey(final String resourceKey) {
      this.resourceKey = resourceKey;
    }

    public String getResourceType() {
      return resourceType;
    }

    public void setResourceType(final String resourceType) {
      this.resourceType = resourceType;
    }

    public String getLinkName() {
      return linkName;
    }

    public void setLinkName(final String linkName) {
      this.linkName = linkName;
    }
  }
}
