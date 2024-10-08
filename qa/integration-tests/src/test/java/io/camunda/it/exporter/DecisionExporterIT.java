/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.exporter;

import org.junit.jupiter.api.Nested;

public class DecisionExporterIT {

  @Nested
  class WithElasticSearchExporter extends ElasticSearchExporterTestKit
      implements DecisionExporterTestKit {}

  /*
  // To add tests with different backends add new nested classes extending from the corresponding testkit
  @Nested
  class WithOpenSearchExporter extends OpenSearchExporterTestKit
      implements DecisionExporterTestKit {}
   */
}
