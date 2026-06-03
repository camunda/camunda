/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.impl.coverage.data;

import java.util.List;
import org.immutables.value.Value;

/**
 * Aggregate coverage input collected during a test run.
 *
 * <p>Groups process and decision instance data together with their corresponding deployed
 * definitions so coverage can be calculated and reported.
 */
@Value.Immutable
public interface CoverageTestData {

  /** Returns process instance execution data captured during the test run. */
  List<CoverageProcessInstanceData> getProcessInstanceData();

  /** Returns decision evaluation data captured during the test run. */
  List<CoverageDecisionInstanceData> getDecisionInstanceData();

  /** Returns process definitions referenced by collected process instance data. */
  List<CoverageProcessDefinitionData> getProcessDefinitionData();

  /** Returns decision definitions referenced by collected decision instance data. */
  List<CoverageDecisionDefinitionData> getDecisionDefinitionData();
}
