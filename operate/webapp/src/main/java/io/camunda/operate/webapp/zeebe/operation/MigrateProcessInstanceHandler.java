/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.zeebe.operation;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.api.command.MigrationPlan;
import io.camunda.client.api.command.MigrationPlanBuilderImpl;
import io.camunda.client.api.command.MigrationPlanImpl;
import io.camunda.operate.webapp.rest.dto.operation.MigrationPlanDto;
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationType;
import java.util.ArrayList;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Operation handler to migrate process instances */
@Component
public class MigrateProcessInstanceHandler extends AbstractOperationHandler
    implements OperationHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(MigrateProcessInstanceHandler.class);

  private final ObjectMapper objectMapper;

  public MigrateProcessInstanceHandler(
      @Qualifier("operateObjectMapper") final ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void handleWithException(final OperationEntity operation) throws Exception {

    final Long processInstanceKey = operation.getProcessInstanceKey();
    if (processInstanceKey == null) {
      failOperation(operation, "No process instance key is provided.");
      return;
    }

    final MigrationPlanDto migrationPlanDto =
        objectMapper.readValue(operation.getMigrationPlan(), MigrationPlanDto.class);
    LOGGER.info(
        "Operation [{}]: Sending Zeebe migrate command for processInstanceKey [{}]...",
        operation.getId(),
        processInstanceKey);
    migrate(processInstanceKey, migrationPlanDto, operation.getId());
    markAsSent(operation);
    LOGGER.info(
        "Operation [{}]: Migrate command sent to Zeebe for processInstanceKey [{}]",
        operation.getId(),
        processInstanceKey);
  }

  @Override
  public Set<OperationType> getTypes() {
    return Set.of(OperationType.MIGRATE_PROCESS_INSTANCE);
  }

  public void migrate(
      final Long processInstanceKey,
      final MigrationPlanDto migrationPlanDto,
      final String operationId) {
    final long targetProcessDefinitionKey =
        Long.parseLong(migrationPlanDto.getTargetProcessDefinitionKey());

    final MigrationPlan migrationPlan =
        new MigrationPlanImpl(targetProcessDefinitionKey, new ArrayList<>());
    migrationPlanDto
        .getMappingInstructions()
        .forEach(
            mapping ->
                migrationPlan
                    .getMappingInstructions()
                    .add(
                        new MigrationPlanBuilderImpl.MappingInstruction(
                            mapping.getSourceElementId(), mapping.getTargetElementId())));

    operationServicesAdapter.migrateProcessInstance(processInstanceKey, migrationPlan, operationId);
  }
}
