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

public class InvoiceBusinessDecisionsDataGenerator extends DecisionDataGenerator {

  private static final String DMN_DIAGRAM = "/diagrams/decision/invoiceBusinessDecisions.dmn";

  private Pair<String, String> inputVarNames = Pair.of("invoiceCategory", "amount");
  private List<Pair<String, Integer>> possibleInputCombinations = ImmutableList.of(
    Pair.of("Misc", RandomUtils.nextInt(0, 250)),
    Pair.of("Misc", RandomUtils.nextInt(250, 1001)),
    Pair.of("Misc", RandomUtils.nextInt(1001, Integer.MAX_VALUE)),
    Pair.of("Travel Expenses", RandomUtils.nextInt()),
    Pair.of("Software License Costs", RandomUtils.nextInt())
  );

  public InvoiceBusinessDecisionsDataGenerator(SimpleEngineClient engineClient, Integer nVersions) {
    super(engineClient, nVersions);
  }

  protected DmnModelInstance retrieveDiagram() {
    return readDecisionDiagram(DMN_DIAGRAM);
  }

  @Override
  protected Map<String, Object> createVariables() {
    final int nextCombinationIndex = RandomUtils.nextInt(0, possibleInputCombinations.size());
    final Pair<String, Integer> nextCombination = possibleInputCombinations.get(nextCombinationIndex);
    Map<String, Object> variables = new HashMap<>();
    variables.put(inputVarNames.getLeft(), nextCombination.getLeft());
    variables.put(inputVarNames.getRight(), nextCombination.getRight());
    return variables;
  }

}
