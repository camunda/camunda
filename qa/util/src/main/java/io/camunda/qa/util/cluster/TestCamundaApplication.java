/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.cluster;

import io.camunda.application.commons.CommonsModuleConfiguration;
import io.camunda.zeebe.qa.util.cluster.TestSpringApplication;

public abstract class TestCamundaApplication<T extends TestCamundaApplication<T>>
    extends TestSpringApplication<T> {

  public TestCamundaApplication(final Class<?> springApplication) {
    super(springApplication, CommonsModuleConfiguration.class);
  }
}
