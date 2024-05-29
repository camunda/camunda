/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.gateway;

import io.camunda.zeebe.qa.util.cluster.TestStandaloneCamunda;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
public class RESTApiTest {

  @TestZeebe final TestStandaloneCamunda testStandaloneCamunda = new TestStandaloneCamunda();

  @Test
  public void shouldAcceptRequest() {

    // given

    // given
    //
    // 1. Elastic - Testcontainer?
    // 2. Broker - Exporter - Importer - Gateway -> Single Application?
    //
    // 5. Deployment of process model
    // 6. Create instance

    // when
    // Query REST API
    // HTTP client? Java client?

    // then
    // Query result is expected - found process instance X

  }
}
