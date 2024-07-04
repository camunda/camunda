/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.client.impl.util;

import java.util.Map;
import org.junit.rules.ExternalResource;

public final class EnvironmentRule extends ExternalResource {

  private Map<String, String> previousEnvironment;

  @Override
  protected void before() throws Throwable {
    previousEnvironment = Environment.system().copy();
  }

  @Override
  protected void after() {
    Environment.system().overwrite(previousEnvironment);
  }
}
