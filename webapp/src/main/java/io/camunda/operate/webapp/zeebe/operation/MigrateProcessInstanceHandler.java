/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.zeebe.operation;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.entities.OperationEntity;
import io.camunda.operate.entities.OperationType;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.store.ProcessStore;
import io.camunda.operate.util.OperationsManager;
import io.camunda.operate.webapp.reader.ProcessReader;
import io.camunda.operate.webapp.rest.dto.operation.MigrationPlanDto;
import io.camunda.zeebe.client.api.command.MigrationPlan;
import io.camunda.zeebe.client.api.command.MigrationPlanBuilderImpl;
import io.camunda.zeebe.client.api.command.MigrationPlanImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Set;

/**
 * Operation handler to migrate process instances
 */
@Component
public class MigrateProcessInstanceHandler extends AbstractOperationHandler implements OperationHandler {

  private static final Logger logger = LoggerFactory.getLogger(MigrateProcessInstanceHandler.class);

  @Autowired
  private OperationsManager operationsManager;

  @Autowired
  private ProcessReader processReader;

  @Autowired
  private ProcessStore processStore;

  @Autowired
  private ListViewTemplate listViewTemplate;

  @Autowired
  private ObjectMapper objectMapper;

  @Override
  public void handleWithException(OperationEntity operation) throws Exception {

    Long processInstanceKey = operation.getProcessInstanceKey();
    if (processInstanceKey == null) {
      failOperation(operation, "No process instance key is provided.");
      return;
    }

    MigrationPlanDto migrationPlanDto = objectMapper.readValue(operation.getMigrationPlan(), MigrationPlanDto.class);
    long targetProcessDefinitionKey = Long.parseLong(migrationPlanDto.getTargetProcessDefinitionKey());

    MigrationPlan migrationPlan = new MigrationPlanImpl(targetProcessDefinitionKey, new ArrayList<>());
    migrationPlanDto.getMappingInstructions().forEach(mapping -> migrationPlan.getMappingInstructions()
        .add(new MigrationPlanBuilderImpl.MappingInstruction(mapping.getSourceElementId(), mapping.getTargetElementId())));

    logger.info(String.format("Operation [%s]: Sending Zeebe migrate command for processInstanceKey [%s]...", operation.getId(), processInstanceKey));
    zeebeClient.newMigrateProcessInstanceCommand(processInstanceKey).migrationPlan(migrationPlan).send().join();
    markAsSent(operation);
    logger.info(String.format("Operation [%s]: Migrate command sent to Zeebe for processInstanceKey [%s]", operation.getId(), processInstanceKey));
  }

  @Override
  public Set<OperationType> getTypes() {
    return Set.of(OperationType.MIGRATE_PROCESS_INSTANCE);
  }
}
