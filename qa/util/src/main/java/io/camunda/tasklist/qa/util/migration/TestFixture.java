/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.qa.util.migration;

/**
 * Test fixture to create test data for given version of Tasklist. Prerequisites: Elasticsearch is
 * running - it is either empty or contains indices of one of previous versions of Tasklist. Test
 * fixture must: * migrate data to newer version of Tasklist (when needed) * run appropriate version
 * of Zeebe * generates data for the given version and * make sure that data was imported to
 * Tasklist
 */
public interface TestFixture {

  void setup(TestContext testContext);

  String getVersion();
}
