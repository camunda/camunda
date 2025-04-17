/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.zeebe.operation.adapter;

import io.camunda.operate.util.ConditionalOnOperateCompatibility;
import io.camunda.operate.webapp.security.permission.PermissionsService;
import io.camunda.operate.webapp.security.tenant.TenantService;
import io.camunda.service.IncidentServices;
import io.camunda.service.JobServices;
import io.camunda.service.JobServices.UpdateJobChangeset;
import io.camunda.service.ProcessInstanceServices;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceCancelRequest;
import io.camunda.service.ResourceServices;
import io.camunda.service.ResourceServices.ResourceDeletionRequest;
import io.camunda.service.VariableServices;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnOperateCompatibility(enabled = "false", matchIfMissing = true)
public class ServicesBasedAdapter implements OperateServicesAdapter {

  private static final Logger LOGGER = LoggerFactory.getLogger(ServicesBasedAdapter.class);

  private final ProcessInstanceServices processInstanceServices;
  private final ResourceServices resourceServices;
  private final VariableServices variableServices;
  private final JobServices<?> jobServices;
  private final IncidentServices incidentServices;
  private final PermissionsService permissionsService;
  private final TenantService tenantService;

  public ServicesBasedAdapter(
      final ProcessInstanceServices processInstanceServices,
      final ResourceServices resourceServices,
      final VariableServices variableServices,
      final JobServices<?> jobServices,
      final IncidentServices incidentServices,
      final PermissionsService permissionsService,
      final TenantService tenantService) {
    this.processInstanceServices = processInstanceServices;
    this.resourceServices = resourceServices;
    this.variableServices = variableServices;
    this.jobServices = jobServices;
    this.incidentServices = incidentServices;
    this.permissionsService = permissionsService;
    this.tenantService = tenantService;
  }

  @Override
  public void deleteResource(final long resourceKey, final String operationId) throws Exception {
    withOperationReference(
        operationReference ->
            resourceServices.deleteResource(
                new ResourceDeletionRequest(resourceKey, operationReference)),
        operationId);
  }

  @Override
  public void cancelProcessInstance(final long processInstanceKey, final String operationId)
      throws Exception {
    withOperationReference(
        operationReference ->
            processInstanceServices.cancelProcessInstance(
                new ProcessInstanceCancelRequest(processInstanceKey, operationReference)),
        operationId);
  }

  @Override
  public void updateJobRetries(final long jobKey, final int retries, final String operationId)
      throws Exception {
    // operationId is not used in the updateJob service method
    jobServices.updateJob(jobKey, new UpdateJobChangeset(retries, null));
  }

  protected static void withOperationReference(final Consumer<Long> command, final String id) {
    try {
      final long operationReference = Long.parseLong(id);
      command.accept(operationReference);
    } catch (final NumberFormatException e) {
      LOGGER.debug(
          "The operation reference provided is not a number: {}. Ignoring propagating it to zeebe commands.",
          id);
    }
  }
}
