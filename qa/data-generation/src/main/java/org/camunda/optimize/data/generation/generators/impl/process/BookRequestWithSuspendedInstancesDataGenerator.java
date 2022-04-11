/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.data.generation.generators.impl.process;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.data.generation.UserAndGroupProvider;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.util.client.SimpleEngineClient;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class BookRequestWithSuspendedInstancesDataGenerator extends ProcessDataGenerator {
  private static final String DIAGRAM = "/diagrams/process/book-request-suspended-instances.bpmn";

  public BookRequestWithSuspendedInstancesDataGenerator(final SimpleEngineClient engineClient,
                                                        final Integer nVersions,
                                                        final UserAndGroupProvider userAndGroupProvider) {
    super(engineClient, nVersions, userAndGroupProvider);
  }

  @Override
  protected void startInstance(final String definitionId, final Map<String, Object> variables) {
    addCorrelatingVariable(variables);
    final ProcessInstanceEngineDto processInstance = engineClient.startProcessInstance(
      definitionId,
      variables,
      getBusinessKey()
    );
    // randomly suspend some process instances
    Random rnd = ThreadLocalRandom.current();
    if (rnd.nextBoolean()) {
      engineClient.suspendProcessInstance(processInstance.getId());
    }
  }


  @Override
  protected BpmnModelInstance retrieveDiagram() {
    return readProcessDiagramAsInstance(DIAGRAM);
  }

  @Override
  protected Map<String, Object> createVariables() {
    return new HashMap<>();
  }
}
