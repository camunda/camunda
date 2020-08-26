/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.engine;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.math3.analysis.function.Gaussian;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.it.extension.EngineDatabaseExtension;
import org.camunda.optimize.test.it.extension.EngineIntegrationExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class OutlierDistributionClient {

  public static final String VARIABLE_1_NAME = "var1";
  public static final String VARIABLE_2_NAME = "var2";
  public static final String VARIABLE_VALUE_OUTLIER = "outlier";
  public static final String VARIABLE_VALUE_NORMAL = "normal";
  public static final String FLOW_NODE_ID_TEST = "testActivity";

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
                                            String firstActivityId) {
    startPIsDistributedByDuration(processDefinition, gaussian, numberOfDataPoints, firstActivityId, null);
  }

  public void startPIsDistributedByDuration(ProcessDefinitionEngineDto processDefinition,
                                            Gaussian gaussian,
                                            int numberOfDataPoints,
                                            String firstActivityId,
                                            String secondActivityId) {
    startPIsDistributedByDuration(
      processDefinition.getId(),
      gaussian,
      numberOfDataPoints,
      firstActivityId,
      secondActivityId,
      0L
    );
  }

  public void startPIsDistributedByDuration(final String processDefinitionId,
                                             final Gaussian gaussian,
                                             final int numberOfDataPoints,
                                             final String firstActivityId,
                                             final String secondActivityId,
                                             final long higherDurationOutlierBoundary) {
    for (int i = 0; i <= numberOfDataPoints; i++) {
      for (int x = 0; x <= gaussian.value(i) * 1000; x++) {
        final long firstActivityDuration = i * 1000L;
        // a more "stretched" distribution on the second activity
        final long secondActivityDuration = Math.round(firstActivityDuration + Math.exp(i) * 1000);
        ProcessInstanceEngineDto processInstance = engineExtension.startProcessInstance(
          processDefinitionId,
          ImmutableMap.of(
            VARIABLE_1_NAME,
            RANDOM.nextInt(),
            VARIABLE_2_NAME,
            secondActivityId != null && secondActivityDuration > higherDurationOutlierBoundary ?
              VARIABLE_VALUE_OUTLIER : VARIABLE_VALUE_NORMAL
          )
        );
        engineDatabaseExtension.changeActivityDuration(processInstance.getId(), firstActivityId, firstActivityDuration);
        engineDatabaseExtension.changeActivityDuration(
          processInstance.getId(),
          secondActivityId,
          secondActivityDuration
        );
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
      engineDatabaseExtension.changeActivityDuration(processInstance.getId(), FLOW_NODE_ID_TEST, 100_000);
      outlierInstanceIds.add(processInstance.getId());
    }

    return outlierInstanceIds;
  }
}
