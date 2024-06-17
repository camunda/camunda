/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.data.generation.generators.impl.decision;

import static io.camunda.optimize.util.DmnModels.INPUT_VARIABLE_GUEST_WITH_CHILDREN;
import static io.camunda.optimize.util.DmnModels.INPUT_VARIABLE_NUMBER_OF_GUESTS;
import static io.camunda.optimize.util.DmnModels.INPUT_VARIABLE_SEASON;

import com.google.common.collect.ImmutableList;
import io.camunda.optimize.test.util.client.SimpleEngineClient;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.tuple.Triple;
import org.camunda.bpm.model.dmn.DmnModelInstance;

public class DecideDishDataGenerator extends DecisionDataGenerator {

  private static final String DMN_DIAGRAM = "/diagrams/decision/decide-dish.dmn";

  private final Triple<String, String, String> inputVarNames =
      Triple.of(
          INPUT_VARIABLE_NUMBER_OF_GUESTS,
          INPUT_VARIABLE_SEASON,
          INPUT_VARIABLE_GUEST_WITH_CHILDREN);
  private final List<Triple<Integer, String, Boolean>> possibleInputCombinations =
      ImmutableList.of(
          Triple.of(RandomUtils.nextInt(0, 11), "Monsoon", RandomUtils.nextBoolean()),
          Triple.of(RandomUtils.nextInt(0, 9), "Fall", RandomUtils.nextBoolean()),
          Triple.of(RandomUtils.nextInt(9, 15), "Fall", RandomUtils.nextBoolean()),
          Triple.of(RandomUtils.nextInt(0, 9), "Winter", RandomUtils.nextBoolean()),
          Triple.of(RandomUtils.nextInt(9, 15), "Winter", RandomUtils.nextBoolean()),
          Triple.of(RandomUtils.nextInt(0, 5), "Spring", RandomUtils.nextBoolean()),
          Triple.of(RandomUtils.nextInt(5, 9), "Spring", RandomUtils.nextBoolean()),
          Triple.of(RandomUtils.nextInt(9, 15), "Spring", RandomUtils.nextBoolean()),
          Triple.of(RandomUtils.nextInt(0, 15), "Summer", RandomUtils.nextBoolean()),
          Triple.of(RandomUtils.nextInt(0, 15), "Summer", RandomUtils.nextBoolean()));

  public DecideDishDataGenerator(final SimpleEngineClient engineClient, final Integer nVersions) {
    super(engineClient, nVersions);
  }

  @Override
  protected DmnModelInstance retrieveDiagram() {
    return readDecisionDiagram(DMN_DIAGRAM);
  }

  @Override
  protected Map<String, Object> createVariables() {
    final int nextCombinationIndex = RandomUtils.nextInt(0, possibleInputCombinations.size());
    final Triple<Integer, String, Boolean> nextCombination =
        possibleInputCombinations.get(nextCombinationIndex);
    final Map<String, Object> variables = new HashMap<>();
    variables.put(inputVarNames.getLeft(), nextCombination.getLeft());
    variables.put(inputVarNames.getMiddle(), nextCombination.getMiddle());
    variables.put(inputVarNames.getRight(), nextCombination.getRight());
    return variables;
  }
}
