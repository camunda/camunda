/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.data.generation.generators.impl.process;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.data.generation.UserAndGroupProvider;
import org.camunda.optimize.test.util.client.SimpleEngineClient;

public class GroupElementsDataGenerator extends ProcessDataGenerator {

  private static final String DIAGRAM = "/diagrams/process/group-elements.bpmn";
  private final Random r = new Random();
  private final String[] firstGatewayOptions = new String[] {"a", "b"};
  private final String[] secondGatewayOptions = new String[] {"c", "d"};

  public GroupElementsDataGenerator(
      final SimpleEngineClient engineClient,
      final Integer nVersions,
      final UserAndGroupProvider userAndGroupProvider) {
    super(engineClient, nVersions, userAndGroupProvider);
  }

  @Override
  protected BpmnModelInstance retrieveDiagram() {
    return readProcessDiagramAsInstance(DIAGRAM);
  }

  @Override
  protected Map<String, Object> createVariables() {
    final Map<String, Object> variables = new HashMap<>();
    variables.put("firstGateway", firstGatewayOptions[r.nextInt(firstGatewayOptions.length)]);
    variables.put("secondGateway", secondGatewayOptions[r.nextInt(secondGatewayOptions.length)]);
    return variables;
  }
}
