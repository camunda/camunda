/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize;

import io.camunda.zeebe.client.api.response.Process;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.test.it.extension.ZeebeExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.RegisterExtension;

public abstract class AbstractZeebeIT extends AbstractIT {

  @RegisterExtension
  @Order(5)
  protected ZeebeExtension zeebeExtension = new ZeebeExtension();

  @BeforeEach
  public void setup() {
    final String embeddedZeebePrefix = zeebeExtension.getZeebeRecordPrefix();
    // set the new record prefix for the next test
    embeddedOptimizeExtension.getConfigurationService().getConfiguredZeebe().setName(embeddedZeebePrefix);
  }

  @AfterEach
  public void after() {
    // Clear all potential existing Zeebe records in Optimize
    elasticSearchIntegrationTestExtension.deleteAllZeebeRecordsForPrefix(zeebeExtension.getZeebeRecordPrefix());
  }

  protected void importAllZeebeEntities() {
    embeddedOptimizeExtension.importAllZeebeEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  protected ProcessInstanceEvent deployAndStartInstanceForProcess(final BpmnModelInstance process) {
    final Process deployedProcess = zeebeExtension.deployProcess(process);
    return zeebeExtension.startProcessInstanceForProcess(deployedProcess.getBpmnProcessId());
  }

}
