/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize;

import org.springframework.boot.test.context.SpringBootTest;

import static org.camunda.optimize.service.util.configuration.EnvironmentPropertiesConstants.INTEGRATION_TESTS;

@SpringBootTest(
  webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
  properties = {INTEGRATION_TESTS + "=true"}
)
// This class serves the purpose of a base class for zeebe performance tests that might reside in maven sub-modules
public abstract class AbstractZeebeImportPerformanceTest {
}
