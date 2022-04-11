/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.data.generation.generators.impl.process;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.data.generation.UserAndGroupProvider;
import org.camunda.optimize.test.util.client.SimpleEngineClient;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class DocumentCheckHandlingDataGenerator extends ProcessDataGenerator {

  private static final String DIAGRAM = "/diagrams/process/document-check-handling.bpmn";

  public DocumentCheckHandlingDataGenerator(final SimpleEngineClient engineClient,
                                            final Integer nVersions,
                                            final UserAndGroupProvider userAndGroupProvider) {
    super(engineClient, nVersions, userAndGroupProvider);
  }

  protected BpmnModelInstance retrieveDiagram() {
    return readProcessDiagramAsInstance(DIAGRAM);
  }

  @Override
  protected Map<String, Object> createVariables() {
    Map<String, Object> variables = new HashMap<>();
    variables.put("approved", ThreadLocalRandom.current().nextDouble());
    return variables;
  }

}
