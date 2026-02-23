/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.spring;

import io.camunda.application.Profile;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms.*$")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
final class CamundaApplicationSpringDependenciesTest extends AbstractSpringDependenciesTest {

  @MultiDbTestApplication
  private static final TestCamundaApplication CAMUNDA_APPLICATION =
      new TestCamundaApplication()
          .withProperty("management.endpoint.beans.access", "unrestricted")
          .withAdditionalProfiles(
              Profile.CONSOLIDATED_AUTH,
              Profile.BROKER,
              Profile.OPERATE,
              Profile.TASKLIST,
              Profile.ADMIN);

  @BeforeEach
  void setUp() {
    fetchBeansGraph(CAMUNDA_APPLICATION.actuatorAddress("beans"));
  }

  @Test
  void shouldHaveNoDependenciesBetweenBrokerAndSearchEngineSchemaInitializer() {
    assertThatNoDependenciesBetween("broker", "searchEngineSchemaInitializer");
  }
}
