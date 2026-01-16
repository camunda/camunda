/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.zeebe.operation.adapter;

import io.camunda.client.api.command.MigrationPlan;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto.Modification;
import java.util.List;
import java.util.Map;

public interface OperateServicesAdapter {

  void deleteResource(
      final long resourceKey, final String operationId, final boolean deleteHistory);

  void migrateProcessInstance(
      final long processInstanceKey, final MigrationPlan migrationPlan, final String operationId);

  void modifyProcessInstance(
      final long processInstanceKey,
      final List<Modification> modifications,
      final String operationId);

  void cancelProcessInstance(final long processInstanceKey, final String operationId);

  void updateJobRetries(final long jobKey, final int retries, final String operationId);

  void resolveIncident(final long incidentKey, final String operationId);

  long setVariables(
      final long scopeKey,
      final Map<String, Object> variables,
      final boolean local,
      final String operationId);

  boolean isExceptionRetriable(final Throwable ex);
}
