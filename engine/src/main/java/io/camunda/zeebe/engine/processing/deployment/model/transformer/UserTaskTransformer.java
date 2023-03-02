/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.model.transformer;

import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.el.impl.StaticExpression;
import io.camunda.zeebe.engine.Loggers;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableJobWorkerTask;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.engine.processing.deployment.model.element.JobWorkerProperties;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.ModelElementTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.TransformContext;
import io.camunda.zeebe.model.bpmn.instance.UserTask;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeAssignmentDefinition;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeFormDefinition;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeHeader;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskHeaders;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskSchedule;
import io.camunda.zeebe.protocol.Protocol;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;

public final class UserTaskTransformer implements ModelElementTransformer<UserTask> {

  private static final Logger LOG = Loggers.STREAM_PROCESSING;

  private final ExpressionLanguage expressionLanguage;

  public UserTaskTransformer(final ExpressionLanguage expressionLanguage) {
    this.expressionLanguage = expressionLanguage;
  }

  @Override
  public Class<UserTask> getType() {
    return UserTask.class;
  }

  @Override
  public void transform(final UserTask element, final TransformContext context) {

    final ExecutableProcess process = context.getCurrentProcess();
    final ExecutableJobWorkerTask userTask =
        process.getElementById(element.getId(), ExecutableJobWorkerTask.class);

    final var jobWorkerProperties = new JobWorkerProperties();
    userTask.setJobWorkerProperties(jobWorkerProperties);

    transformTaskDefinition(jobWorkerProperties);
    transformAssignmentDefinition(element, jobWorkerProperties);
    transformTaskSchedule(element, jobWorkerProperties);
    transformTaskHeaders(element, jobWorkerProperties);
  }

  private void transformTaskDefinition(final JobWorkerProperties jobWorkerProperties) {
    jobWorkerProperties.setType(new StaticExpression(Protocol.USER_TASK_JOB_TYPE));
    jobWorkerProperties.setRetries(new StaticExpression("1"));
  }

  private void transformAssignmentDefinition(
      final UserTask element, final JobWorkerProperties jobWorkerProperties) {
    final var assignmentDefinition =
        element.getSingleExtensionElement(ZeebeAssignmentDefinition.class);
    if (assignmentDefinition == null) {
      return;
    }
    final var assignee = assignmentDefinition.getAssignee();
    if (assignee != null && !assignee.isBlank()) {
      jobWorkerProperties.setAssignee(expressionLanguage.parseExpression(assignee));
    }
    final var candidateGroups = assignmentDefinition.getCandidateGroups();
    if (candidateGroups != null && !candidateGroups.isBlank()) {
      final var candidateGroupsExpression = expressionLanguage.parseExpression(candidateGroups);
      if (candidateGroupsExpression.isStatic()) {
        // static candidateGroups must be in CSV format, but this is already checked by validator
        jobWorkerProperties.setCandidateGroups(
            ExpressionTransformer.parseListOfCsv(candidateGroups)
                .map(ExpressionTransformer::asListLiteral)
                .map(ExpressionTransformer::asFeelExpressionString)
                .map(expressionLanguage::parseExpression)
                .get());
      } else {
        jobWorkerProperties.setCandidateGroups(candidateGroupsExpression);
      }
    }
    final var candidateUsers = assignmentDefinition.getCandidateUsers();
    if (candidateUsers != null && !candidateUsers.isBlank()) {
      final var candidateUsersExpression = expressionLanguage.parseExpression(candidateUsers);
      if (candidateUsersExpression.isStatic()) {
        // static candidateUsers must be in CSV format, but this is already checked by validator
        jobWorkerProperties.setCandidateUsers(
            ExpressionTransformer.parseListOfCsv(candidateUsers)
                .map(ExpressionTransformer::asListLiteral)
                .map(ExpressionTransformer::asFeelExpressionString)
                .map(expressionLanguage::parseExpression)
                .get());
      } else {
        jobWorkerProperties.setCandidateUsers(candidateUsersExpression);
      }
    }
  }

  private void transformTaskSchedule(
      final UserTask element, final JobWorkerProperties jobWorkerProperties) {

    final var taskSchedule = element.getSingleExtensionElement(ZeebeTaskSchedule.class);
    if (taskSchedule == null) {
      return;
    }

    final var dueDate = taskSchedule.getDueDate();
    if (dueDate != null && !dueDate.isBlank()) {
      jobWorkerProperties.setDueDate(expressionLanguage.parseExpression(dueDate));
    }

    final var followUpDate = taskSchedule.getFollowUpDate();
    if (followUpDate != null && !followUpDate.isBlank()) {
      jobWorkerProperties.setFollowUpDate(expressionLanguage.parseExpression(followUpDate));
    }
  }

  private void transformTaskHeaders(
      final UserTask element, final JobWorkerProperties jobWorkerProperties) {
    final Map<String, String> taskHeaders = new HashMap<>();

    collectModelTaskHeaders(element, taskHeaders);

    addZeebeUserTaskFormKeyHeader(element, taskHeaders);

    if (!taskHeaders.isEmpty()) {
      jobWorkerProperties.setTaskHeaders(taskHeaders);
    }
  }

  private void addZeebeUserTaskFormKeyHeader(
      final UserTask element, final Map<String, String> taskHeaders) {
    final ZeebeFormDefinition formDefinition =
        element.getSingleExtensionElement(ZeebeFormDefinition.class);

    if (formDefinition != null) {
      taskHeaders.put(Protocol.USER_TASK_FORM_KEY_HEADER_NAME, formDefinition.getFormKey());
    }
  }

  private void collectModelTaskHeaders(
      final UserTask element, final Map<String, String> taskHeaders) {
    final ZeebeTaskHeaders modelTaskHeaders =
        element.getSingleExtensionElement(ZeebeTaskHeaders.class);

    if (modelTaskHeaders != null) {
      final List<ZeebeHeader> validHeaders =
          modelTaskHeaders.getHeaders().stream()
              .filter(this::isValidHeader)
              .collect(Collectors.toList());

      if (validHeaders.size() < modelTaskHeaders.getHeaders().size()) {
        LOG.warn(
            "Ignoring invalid headers for task '{}'. Must have non-empty key and value.",
            element.getName());
      }

      validHeaders.forEach(h -> taskHeaders.put(h.getKey(), h.getValue()));
    }
  }

  private boolean isValidHeader(final ZeebeHeader header) {
    return header != null && isValidHeader(header.getKey(), header.getValue());
  }

  private boolean isValidHeader(final String key, final String value) {
    return key != null && !key.isEmpty() && value != null && !value.isEmpty();
  }
}
