/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http;

import io.camunda.service.JobServices.UpdateJobChangeset;
import io.camunda.zeebe.protocol.impl.record.value.job.JobResult;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import java.util.Map;

/**
 * Intermediate records returned by {@link RequestMapper} command-mapping methods. These bundle
 * validated fields into a single return value for consumption by service adapters.
 *
 * <p>The service layer ({@code JobServices}, {@code UserTaskServices}, etc.) accepts individual
 * scalar parameters rather than DTOs, because it is shared across REST, gRPC, MCP, Operate, and
 * Tasklist callers — each of which constructs scalars from its own input types. These records serve
 * as the adapter seam between the HTTP mapping layer and the service layer.
 */
public final class MappedCommandRequests {

  private MappedCommandRequests() {}

  public record CompleteUserTaskRequest(
      long userTaskKey, Map<String, Object> variables, String action) {}

  public record UpdateUserTaskRequest(long userTaskKey, UserTaskRecord changeset, String action) {}

  public record AssignUserTaskRequest(
      long userTaskKey, String assignee, String action, boolean allowOverride) {}

  public record FailJobRequest(
      long jobKey,
      int retries,
      String errorMessage,
      Long retryBackoff,
      Map<String, Object> variables) {}

  public record ErrorJobRequest(
      long jobKey, String errorCode, String errorMessage, Map<String, Object> variables) {}

  public record CompleteJobRequest(long jobKey, Map<String, Object> variables, JobResult result) {}

  public record UpdateJobRequest(
      long jobKey, Long operationReference, UpdateJobChangeset changeset) {}

  public record BroadcastSignalRequest(
      String signalName, Map<String, Object> variables, String tenantId) {}

  public record DecisionEvaluationRequest(
      String decisionId, Long decisionKey, Map<String, Object> variables, String tenantId) {}
}
