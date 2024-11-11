/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.security.auth.Authentication;
import io.camunda.service.processtest.ProcessTestRunner;
import io.camunda.service.processtest.TestSpecificationResult;
import io.camunda.service.processtest.dsl.TestSpecification;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;

public class ProcessTestServices extends ApiServices<ProcessTestServices> {

  private final ProcessTestRunner processTestRunner = new ProcessTestRunner();

  public ProcessTestServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final Authentication authentication) {
    super(brokerClient, securityContextProvider, authentication);
  }

  @Override
  public ProcessTestServices withAuthentication(final Authentication authentication) {
    return new ProcessTestServices(brokerClient, securityContextProvider, authentication);
  }

  public TestSpecificationResult execute(final TestSpecification testSpecification) {
    return processTestRunner.run(testSpecification);
  }
}
