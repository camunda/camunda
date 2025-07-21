/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize;

import io.camunda.optimize.service.LocalizationFileTest;
import io.camunda.optimize.service.OutlierAnalysisServiceTest;
import io.camunda.optimize.service.ProcessOverviewServiceTest;
import org.junit.platform.suite.api.ExcludePackages;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectPackages({"io.camunda.optimize"})
@SelectClasses({
  LocalizationFileTest.class,
  OutlierAnalysisServiceTest.class,
  ProcessOverviewServiceTest.class
})
@ExcludePackages({"io.camunda.optimize.service.backup", "io.camunda.optimize.service.importing"})
public class OptimizeCoreFeaturesTestSuite {}
