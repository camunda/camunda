/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 */
package io.camunda.zeebe.gateway.rest.controller.generated;

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedUserTaskAssignmentRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedUserTaskAuditLogSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedUserTaskCompletionRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedUserTaskSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedUserTaskUpdateRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedUserTaskVariableSearchQueryRequestStrictContract;
import io.camunda.security.auth.CamundaAuthentication;
import jakarta.annotation.Generated;
import org.springframework.http.ResponseEntity;

/**
 * Service adapter for UserTask operations.
 * Implements request mapping, service delegation, and response construction.
 */
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public interface UserTaskServiceAdapter {

  ResponseEntity<Void> completeUserTask(
      Long userTaskKey,
      GeneratedUserTaskCompletionRequestStrictContract userTaskCompletionRequest,
      CamundaAuthentication authentication
  );

  ResponseEntity<Void> assignUserTask(
      Long userTaskKey,
      GeneratedUserTaskAssignmentRequestStrictContract userTaskAssignmentRequest,
      CamundaAuthentication authentication
  );

  ResponseEntity<Object> getUserTask(
      Long userTaskKey,
      CamundaAuthentication authentication
  );

  ResponseEntity<Void> updateUserTask(
      Long userTaskKey,
      GeneratedUserTaskUpdateRequestStrictContract userTaskUpdateRequest,
      CamundaAuthentication authentication
  );

  ResponseEntity<Object> getUserTaskForm(
      Long userTaskKey,
      CamundaAuthentication authentication
  );

  ResponseEntity<Void> unassignUserTask(
      Long userTaskKey,
      CamundaAuthentication authentication
  );

  ResponseEntity<Object> searchUserTasks(
      GeneratedUserTaskSearchQueryRequestStrictContract userTaskSearchQuery,
      CamundaAuthentication authentication
  );

  ResponseEntity<Object> searchUserTaskVariables(
      Long userTaskKey,
      Boolean truncateValues,
      GeneratedUserTaskVariableSearchQueryRequestStrictContract userTaskVariableSearchQueryRequest,
      CamundaAuthentication authentication
  );

  ResponseEntity<Object> searchUserTaskAuditLogs(
      Long userTaskKey,
      GeneratedUserTaskAuditLogSearchQueryRequestStrictContract userTaskAuditLogSearchQueryRequest,
      CamundaAuthentication authentication
  );
}
