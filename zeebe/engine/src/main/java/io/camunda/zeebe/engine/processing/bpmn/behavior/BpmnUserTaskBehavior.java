/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn.behavior;

import io.camunda.zeebe.el.Expression;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableUserTask;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.deployment.PersistedForm;
import io.camunda.zeebe.engine.state.immutable.FormState;
import io.camunda.zeebe.engine.state.immutable.UserTaskState.LifecycleState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.engine.state.mutable.MutableUserTaskState;
import io.camunda.zeebe.msgpack.value.DocumentValue;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.scheduler.clock.ActorClock;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.Either;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BpmnUserTaskBehavior {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(BpmnUserTaskBehavior.class.getPackageName());
  private static final Set<LifecycleState> CANCELABLE_LIFECYCLE_STATES =
      EnumSet.complementOf(EnumSet.of(LifecycleState.NOT_FOUND, LifecycleState.CANCELING));
  private final UserTaskRecord userTaskRecord =
      new UserTaskRecord().setVariables(DocumentValue.EMPTY_DOCUMENT);

  private final HeaderEncoder headerEncoder = new HeaderEncoder(LOGGER);
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final ExpressionProcessor expressionBehavior;
  private final BpmnStateBehavior stateBehavior;
  private final FormState formState;
  private final MutableUserTaskState userTaskState;

  public BpmnUserTaskBehavior(
      final KeyGenerator keyGenerator,
      final Writers writers,
      final ExpressionProcessor expressionBehavior,
      final BpmnStateBehavior stateBehavior,
      final FormState formState,
      final MutableUserTaskState userTaskState) {
    this.keyGenerator = keyGenerator;
    stateWriter = writers.state();
    this.expressionBehavior = expressionBehavior;
    this.stateBehavior = stateBehavior;
    this.formState = formState;
    this.userTaskState = userTaskState;
  }

  public Either<Failure, UserTaskProperties> evaluateUserTaskExpressions(
      final ExecutableUserTask element, final BpmnElementContext context) {
    final var userTaskProps = element.getUserTaskProperties();
    final var scopeKey = context.getElementInstanceKey();
    final var tenantId = context.getTenantId();
    return Either.<Failure, UserTaskProperties>right(new UserTaskProperties())
        .flatMap(
            p -> evaluateAssigneeExpression(userTaskProps.getAssignee(), scopeKey).map(p::assignee))
        .flatMap(
            p ->
                evaluateCandidateGroupsExpression(userTaskProps.getCandidateGroups(), scopeKey)
                    .map(p::candidateGroups))
        .flatMap(
            p ->
                evaluateCandidateUsersExpression(userTaskProps.getCandidateUsers(), scopeKey)
                    .map(p::candidateUsers))
        .flatMap(p -> evaluateDateExpression(userTaskProps.getDueDate(), scopeKey).map(p::dueDate))
        .flatMap(
            p ->
                evaluateDateExpression(userTaskProps.getFollowUpDate(), scopeKey)
                    .map(p::followUpDate))
        .flatMap(
            p ->
                evaluateFormIdExpressionToFormKey(userTaskProps.getFormId(), scopeKey, tenantId)
                    .map(p::formKey))
        .flatMap(
            p ->
                evaluateExternalFormReferenceExpression(
                        userTaskProps.getExternalFormReference(), scopeKey)
                    .map(p::externalFormReference));
  }

  public UserTaskRecord createNewUserTask(
      final BpmnElementContext context,
      final ExecutableUserTask element,
      final UserTaskProperties userTaskProperties) {
    final var userTaskKey = keyGenerator.nextKey();

    final var encodedHeaders =
        headerEncoder.encode(element.getUserTaskProperties().getTaskHeaders());

    userTaskRecord
        .setUserTaskKey(userTaskKey)
        .setAssignee(userTaskProperties.getAssignee())
        .setCandidateGroupsList(userTaskProperties.getCandidateGroups())
        .setCandidateUsersList(userTaskProperties.getCandidateUsers())
        .setDueDate(userTaskProperties.getDueDate())
        .setFollowUpDate(userTaskProperties.getFollowUpDate())
        .setFormKey(userTaskProperties.getFormKey())
        .setExternalFormReference(userTaskProperties.getExternalFormReference())
        .setCustomHeaders(encodedHeaders)
        .setBpmnProcessId(context.getBpmnProcessId())
        .setProcessDefinitionVersion(context.getProcessVersion())
        .setProcessDefinitionKey(context.getProcessDefinitionKey())
        .setProcessInstanceKey(context.getProcessInstanceKey())
        .setElementId(element.getId())
        .setElementInstanceKey(context.getElementInstanceKey())
        .setTenantId(context.getTenantId())
        .setCreationTimestamp(ActorClock.currentTimeMillis());

    stateWriter.appendFollowUpEvent(userTaskKey, UserTaskIntent.CREATING, userTaskRecord);
    return userTaskRecord;
  }

  public Either<Failure, String> evaluateAssigneeExpression(
      final Expression assignee, final long scopeKey) {
    if (assignee == null) {
      return Either.right(null);
    }
    return expressionBehavior.evaluateStringExpression(assignee, scopeKey);
  }

  public Either<Failure, List<String>> evaluateCandidateGroupsExpression(
      final Expression candidateGroups, final long scopeKey) {
    if (candidateGroups == null) {
      return Either.right(null);
    }
    return expressionBehavior.evaluateArrayOfStringsExpression(candidateGroups, scopeKey);
  }

  public Either<Failure, List<String>> evaluateCandidateUsersExpression(
      final Expression candidateUsers, final long scopeKey) {
    if (candidateUsers == null) {
      return Either.right(null);
    }
    return expressionBehavior.evaluateArrayOfStringsExpression(candidateUsers, scopeKey);
  }

  public Either<Failure, String> evaluateDateExpression(
      final Expression date, final long scopeKey) {
    if (date == null) {
      return Either.right(null);
    }
    return expressionBehavior
        .evaluateDateTimeExpression(date, scopeKey, true)
        .map(optionalDate -> optionalDate.map(ZonedDateTime::toString).orElse(null));
  }

  public Either<Failure, Long> evaluateFormIdExpressionToFormKey(
      final Expression formIdExpression, final long scopeKey, final String tenantId) {
    if (formIdExpression == null) {
      return Either.right(null);
    }
    return expressionBehavior
        .evaluateStringExpression(formIdExpression, scopeKey)
        .flatMap(
            formId -> {
              final Optional<PersistedForm> latestFormById =
                  formState.findLatestFormById(formId, tenantId);
              return latestFormById
                  .<Either<Failure, Long>>map(
                      persistedForm -> Either.right(persistedForm.getFormKey()))
                  .orElseGet(
                      () ->
                          Either.left(
                              new Failure(
                                  String.format(
                                      "Expected to find a form with id '%s',"
                                          + " but no form with this id is found,"
                                          + " at least a form with this id should be available."
                                          + " To resolve the Incident please deploy a form with the same id",
                                      formId),
                                  ErrorType.FORM_NOT_FOUND,
                                  scopeKey)));
            });
  }

  public Either<Failure, String> evaluateExternalFormReferenceExpression(
      final Expression externalFormReference, final long scopeKey) {
    if (externalFormReference == null) {
      return Either.right(null);
    }
    return expressionBehavior.evaluateStringExpression(externalFormReference, scopeKey);
  }

  public void cancelUserTask(final BpmnElementContext context) {
    final var elementInstance = stateBehavior.getElementInstance(context);
    cancelUserTask(elementInstance);
  }

  public void cancelUserTask(final ElementInstance elementInstance) {
    final long userTaskKey = elementInstance.getUserTaskKey();
    if (userTaskKey > 0) {
      final LifecycleState lifecycleState = userTaskState.getLifecycleState(userTaskKey);
      if (CANCELABLE_LIFECYCLE_STATES.contains(lifecycleState)) {
        final UserTaskRecord userTask = userTaskState.getUserTask(userTaskKey);
        stateWriter.appendFollowUpEvent(userTaskKey, UserTaskIntent.CANCELING, userTask);
        stateWriter.appendFollowUpEvent(userTaskKey, UserTaskIntent.CANCELED, userTask);
      }
    }
  }

  public void userTaskCreated(final UserTaskRecord userTaskRecord) {
    final long userTaskKey = userTaskRecord.getUserTaskKey();
    stateWriter.appendFollowUpEvent(userTaskKey, UserTaskIntent.CREATED, userTaskRecord);
  }

  public static final class UserTaskProperties {

    private String assignee;
    private List<String> candidateGroups;
    private List<String> candidateUsers;
    private String dueDate;
    private String externalFormReference;
    private String followUpDate;
    private Long formKey;

    public String getAssignee() {
      return getOrEmpty(assignee);
    }

    public UserTaskProperties assignee(final String assignee) {
      this.assignee = assignee;
      return this;
    }

    public List<String> getCandidateGroups() {
      return getOrEmpty(candidateGroups);
    }

    public UserTaskProperties candidateGroups(final List<String> candidateGroups) {
      this.candidateGroups = candidateGroups;
      return this;
    }

    public List<String> getCandidateUsers() {
      return getOrEmpty(candidateUsers);
    }

    public UserTaskProperties candidateUsers(final List<String> candidateUsers) {
      this.candidateUsers = candidateUsers;
      return this;
    }

    public String getDueDate() {
      return getOrEmpty(dueDate);
    }

    public UserTaskProperties dueDate(final String dueDate) {
      this.dueDate = dueDate;
      return this;
    }

    public String getExternalFormReference() {
      return getOrEmpty(externalFormReference);
    }

    public UserTaskProperties externalFormReference(final String externalFormReference) {
      this.externalFormReference = externalFormReference;
      return this;
    }

    public String getFollowUpDate() {
      return getOrEmpty(followUpDate);
    }

    public UserTaskProperties followUpDate(final String followUpDate) {
      this.followUpDate = followUpDate;
      return this;
    }

    public Long getFormKey() {
      return formKey == null ? -1 : formKey;
    }

    public UserTaskProperties formKey(final Long formKey) {
      this.formKey = formKey;
      return this;
    }

    private static String getOrEmpty(final String stringValue) {
      return stringValue == null ? "" : stringValue;
    }

    private static List<String> getOrEmpty(final List<String> listValue) {
      return listValue == null ? Collections.emptyList() : listValue;
    }
  }
}
