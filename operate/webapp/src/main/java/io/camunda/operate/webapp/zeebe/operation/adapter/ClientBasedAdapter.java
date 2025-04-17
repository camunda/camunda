/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.zeebe.operation.adapter;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.CommandWithOperationReferenceStep;
import io.camunda.operate.util.ConditionalOnOperateCompatibility;
import io.camunda.operate.webapp.security.permission.PermissionsService;
import io.camunda.operate.webapp.security.tenant.TenantService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnOperateCompatibility(enabled = "true")
public class ClientBasedAdapter implements OperateServicesAdapter {

  private static final Logger LOGGER = LoggerFactory.getLogger(ClientBasedAdapter.class);

  private final CamundaClient camundaClient;
  private final PermissionsService permissionsService;
  private final TenantService tenantService;

  public ClientBasedAdapter(
      final CamundaClient camundaClient,
      final PermissionsService permissionsService,
      final TenantService tenantService) {
    this.camundaClient = camundaClient;
    this.permissionsService = permissionsService;
    this.tenantService = tenantService;
  }

  @Override
  public void deleteResource(final long resourceKey, final String operationId) throws Exception {
    final var deleteResourceCommand =
        withOperationReference(camundaClient.newDeleteResourceCommand(resourceKey), operationId);
    deleteResourceCommand.send().join();
  }

  @Override
  public void cancelProcessInstance(final long processInstanceKey, final String operationId)
      throws Exception {
    final var cancelInstanceCommand =
        withOperationReference(
            camundaClient.newCancelInstanceCommand(processInstanceKey), operationId);
    cancelInstanceCommand.send().join();
  }

  protected static <T extends CommandWithOperationReferenceStep<T>> T withOperationReference(
      final T command, final String id) {
    try {
      final long operationReference = Long.parseLong(id);
      command.operationReference(operationReference);
    } catch (final NumberFormatException e) {
      LOGGER.debug(
          "The operation reference provided is not a number: {}. Ignoring propagating it to zeebe commands.",
          id);
    }
    return command;
  }
}
