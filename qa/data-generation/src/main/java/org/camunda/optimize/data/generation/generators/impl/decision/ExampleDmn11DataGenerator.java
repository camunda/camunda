/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.data.generation.generators.impl.decision;

import com.google.common.collect.ImmutableList;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.optimize.test.util.client.SimpleEngineClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExampleDmn11DataGenerator extends DecisionDataGenerator {

  private static final String DMN_DIAGRAM = "/diagrams/decision/Example-DMN-1.1.dmn";

  private Pair<String, String> inputVarNames = Pair.of("status", "sum");
  private List<Pair<String, Double>> possibleInputCombinations = ImmutableList.of(
    Pair.of("bronze", RandomUtils.nextDouble()),
    Pair.of("silver", RandomUtils.nextDouble(0, 999.99)),
    Pair.of("silver", RandomUtils.nextDouble(1000, Double.MAX_VALUE)),
    Pair.of("gold", RandomUtils.nextDouble())
  );

  public ExampleDmn11DataGenerator(SimpleEngineClient engineClient, Integer nVersions) {
    super(engineClient, nVersions);
  }

  protected DmnModelInstance retrieveDiagram() {
    return readDecisionDiagram(DMN_DIAGRAM);
  }

  @Override
  protected Map<String, Object> createVariables() {
    final int nextCombinationIndex = RandomUtils.nextInt(0, possibleInputCombinations.size());
    final Pair<String, Double> nextCombination = possibleInputCombinations.get(nextCombinationIndex);
    Map<String, Object> variables = new HashMap<>();
    variables.put(inputVarNames.getLeft(), nextCombination.getLeft());
    variables.put(inputVarNames.getRight(), nextCombination.getRight());
    return variables;
  }

}
