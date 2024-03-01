/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.test.engine;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.math3.analysis.function.Gaussian;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.it.extension.EngineDatabaseExtension;
import org.camunda.optimize.test.it.extension.EngineIntegrationExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class OutlierDistributionClient {

  public static final String VARIABLE_1_NAME = "var1";
  public static final String VARIABLE_2_NAME = "var2";
  public static final String VARIABLE_VALUE_OUTLIER = "outlier";
  public static final String VARIABLE_VALUE_NORMAL = "normal";
  public static final String FLOW_NODE_ID_TEST = "testActivity";
  public static final String ANOTHER_FLOW_NODE_ID_TEST = "testActivity2";
  public static final String SPLITTING_GATEWAY_LABEL = "goToTask1";

  private static final Random RANDOM = new Random();

  private final EngineIntegrationExtension engineExtension;
  private final EngineDatabaseExtension engineDatabaseExtension;

  public OutlierDistributionClient(final EngineIntegrationExtension engineExtension) {
    this.engineExtension = engineExtension;
    this.engineDatabaseExtension = new EngineDatabaseExtension(engineExtension.getEngineName());
  }

  public void startPIsDistributedByDuration(ProcessDefinitionEngineDto processDefinition,
                                            Gaussian gaussian,
                                            int numberOfDataPoints,
                                            final String... activityIds) {
    startPIsDistributedByDuration(
      processDefinition.getId(), gaussian, numberOfDataPoints, 0L, activityIds
    );
  }

  public void startPIsDistributedByDuration(final String processDefinitionId,
                                            final Gaussian gaussian,
                                            final int numberOfDataPoints,
                                            final long higherDurationOutlierBoundary,
                                            final String... activityIds) {
    if (activityIds.length == 0) {
      throw new IllegalArgumentException("At least one activityId is required");
    }
    for (int i = 0; i <= numberOfDataPoints; i++) {
      for (int x = 0; x <= gaussian.value(i) * 1000; x++) {
        final long firstActivityDuration = i * 1000L;
        // a more "stretched" distribution on all other activities
        final long remainingActivitiesDuration = Math.round(firstActivityDuration + Math.exp(i) * 1000);
        Map<String, Object> variables = new HashMap<>();
        variables.put(SPLITTING_GATEWAY_LABEL, true);
        variables.put(VARIABLE_1_NAME, RANDOM.nextInt());
        variables.put(VARIABLE_2_NAME, activityIds.length > 1 && remainingActivitiesDuration > higherDurationOutlierBoundary ?
          VARIABLE_VALUE_OUTLIER : VARIABLE_VALUE_NORMAL);
        ProcessInstanceEngineDto processInstance = engineExtension.startProcessInstance(
          processDefinitionId,
          variables
        );
        engineDatabaseExtension.changeFlowNodeTotalDuration(processInstance.getId(), activityIds[0], firstActivityDuration);
        for (int activityIndex = 1; activityIndex < activityIds.length; activityIndex++) {
          engineDatabaseExtension.changeFlowNodeTotalDuration(
            processInstance.getId(), activityIds[activityIndex], remainingActivitiesDuration
          );
        }
      }
    }
  }

  public List<String> createNormalDistributionAnd3Outliers(final ProcessDefinitionEngineDto processDefinition,
                                                           final String outlierVariable2Value) {
    // a couple of normally distributed instances
    startPIsDistributedByDuration(
      processDefinition, new Gaussian(10. / 2., 15), 5, FLOW_NODE_ID_TEST
    );

    List<String> outlierInstanceIds = new ArrayList<>();
    // 3 higher outlier instance
    // 3 is the minDoc count for which terms are considered to eliminate high cardinality variables
    for (int i = 0; i < 3; i++) {
      ProcessInstanceEngineDto processInstance = engineExtension.startProcessInstance(
        processDefinition.getId(),
        // VAR2 has the same value as all non outliers
        ImmutableMap.of(VARIABLE_1_NAME, RANDOM.nextInt(), VARIABLE_2_NAME, outlierVariable2Value)
      );
      engineDatabaseExtension.changeFlowNodeTotalDuration(processInstance.getId(), FLOW_NODE_ID_TEST, 100_000);
      outlierInstanceIds.add(processInstance.getId());
    }

    return outlierInstanceIds;
  }
}
