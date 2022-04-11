/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.data.generation.generators.impl.process;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.data.generation.UserAndGroupProvider;
import org.camunda.optimize.test.util.client.SimpleEngineClient;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;

import static org.apache.commons.lang3.RandomStringUtils.random;
import static org.apache.commons.lang3.RandomUtils.nextInt;

public class DRIProcessWithLoadsOfVariablesDataGenerator extends ProcessDataGenerator {

  private static final String DIAGRAM = "/diagrams/process/dri-process.bpmn";
  private static final String[] REVIEW_GATEWAY_OPTIONS = new String[]{"yes", "no"};

  private final String[] variableNames;

  public DRIProcessWithLoadsOfVariablesDataGenerator(final SimpleEngineClient engineClient,
                                                     final Integer nVersions,
                                                     final UserAndGroupProvider userAndGroupProvider) {
    super(engineClient, nVersions, userAndGroupProvider);
    this.variableNames = IntStream.range(0, 100)
      .mapToObj(i -> random(15, true, false))
      .toArray(String[]::new);
  }

  protected BpmnModelInstance retrieveDiagram() {
    return readProcessDiagramAsInstance(DIAGRAM);
  }

  @Override
  protected Map<String, Object> createVariables() {
    Map<String, Object> variables = new HashMap<>();
    variables.put("doReview", REVIEW_GATEWAY_OPTIONS[nextInt(0, REVIEW_GATEWAY_OPTIONS.length)]);
    variables.put("loopCardinality", nextInt(1, 6));
    Arrays.stream(variableNames).forEach(variableName -> variables.put(variableName, nextInt()));
    return variables;
  }
}
