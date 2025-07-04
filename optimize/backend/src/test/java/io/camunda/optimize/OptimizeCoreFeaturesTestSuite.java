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
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectPackages({
  "io.camunda.optimize.exception",
  "io.camunda.optimize.test.util",
  "io.camunda.optimize.rest",
  "io.camunda.optimize.service.alert",
  "io.camunda.optimize.service.cleanup",
  "io.camunda.optimize.service.db",
  "io.camunda.optimize.service.export",
  "io.camunda.optimize.service.identity",
  "io.camunda.optimize.service.mixpanel",
  "io.camunda.optimize.service.panelnotification",
  "io.camunda.optimize.service.report",
  "io.camunda.optimize.service.schema.type",
  "io.camunda.optimize.service.security",
  "io.camunda.optimize.service.tenant",
  "io.camunda.optimize.service.uiconfiguration",
  "io.camunda.optimize.service.util"
})
@SelectClasses({
  LocalizationFileTest.class,
  OutlierAnalysisServiceTest.class,
  ProcessOverviewServiceTest.class
})
public class OptimizeCoreFeaturesTestSuite {}
