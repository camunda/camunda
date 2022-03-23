/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.data.generation.generators.impl.decision;

import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.optimize.test.util.client.SimpleEngineClient;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

public class BerStatusDateInputDecisionDataGenerator extends DecisionDataGenerator {

  private static final String DMN_DIAGRAM = "/diagrams/decision/berStatusDateInputDecision.dmn";

  private final Pair<String, String> inputVarNames = Pair.of("flightDestination", "date");
  private final List<Pair<String, Date>> possibleInputCombinations;

  public BerStatusDateInputDecisionDataGenerator(SimpleEngineClient engineClient, Integer nVersions) {
    super(engineClient, nVersions);

    // create some date vars within the last 10 years and the last
    // 10 mins for date variable reports with different groupBy units
    possibleInputCombinations = new ArrayList<>();
    IntStream.range(0, 10)
      .forEach(
        i -> {
          final long tenMinsAgo = ZonedDateTime.now().minusMinutes(10).toInstant().toEpochMilli();
          final long tenYearsAgo = ZonedDateTime.now().minusYears(10).toInstant().toEpochMilli();
          final long randomWithinLastTenYears = ThreadLocalRandom.current()
            .nextLong(tenYearsAgo, Instant.now().toEpochMilli());
          final long randomWithinLastTenMins = ThreadLocalRandom.current()
            .nextLong(tenMinsAgo, Instant.now().toEpochMilli());
          possibleInputCombinations.add(Pair.of("Glasgow", new Date(randomWithinLastTenYears)));
          possibleInputCombinations.add(Pair.of("Omran", new Date(randomWithinLastTenYears)));
          possibleInputCombinations.add(Pair.of("Malaga", new Date(randomWithinLastTenMins)));
        }
      );
  }

  @Override
  protected DmnModelInstance retrieveDiagram() {
    return readDecisionDiagram(DMN_DIAGRAM);
  }

  @Override
  protected Map<String, Object> createVariables() {
    final int nextCombinationIndex = RandomUtils.nextInt(0, possibleInputCombinations.size());
    final Pair<String, Date> nextCombination = possibleInputCombinations.get(nextCombinationIndex);
    Map<String, Object> variables = new HashMap<>();
    variables.put(inputVarNames.getLeft(), nextCombination.getLeft());
    variables.put(inputVarNames.getRight(), nextCombination.getRight());
    return variables;
  }
}
