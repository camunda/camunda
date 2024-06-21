/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.data.generation.generators.impl.process;

import static org.apache.commons.lang3.RandomStringUtils.random;
import static org.apache.commons.lang3.RandomUtils.nextInt;

import io.camunda.optimize.data.generation.UserAndGroupProvider;
import io.camunda.optimize.test.util.client.SimpleEngineClient;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;

public class DRIProcessWithLoadsOfVariablesDataGenerator extends ProcessDataGenerator {

  private static final String DIAGRAM = "/diagrams/process/dri-process.bpmn";
  private static final String[] REVIEW_GATEWAY_OPTIONS = new String[]{"yes", "no"};

  private final String[] variableNames;

  public DRIProcessWithLoadsOfVariablesDataGenerator(
      final SimpleEngineClient engineClient,
      final Integer nVersions,
      final UserAndGroupProvider userAndGroupProvider) {
    super(engineClient, nVersions, userAndGroupProvider);
    variableNames =
        IntStream.range(0, 100).mapToObj(i -> random(15, true, false)).toArray(String[]::new);
  }

  @Override
  protected BpmnModelInstance retrieveDiagram() {
    return readProcessDiagramAsInstance(DIAGRAM);
  }

  @Override
  protected Map<String, Object> createVariables() {
    final Map<String, Object> variables = new HashMap<>();
    variables.put("doReview", REVIEW_GATEWAY_OPTIONS[nextInt(0, REVIEW_GATEWAY_OPTIONS.length)]);
    variables.put("loopCardinality", nextInt(1, 6));
    Arrays.stream(variableNames).forEach(variableName -> variables.put(variableName, nextInt()));
    return variables;
  }
}
