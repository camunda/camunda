/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.test;

import io.camunda.zeebe.engine.DefaultEngineFactory;
import io.camunda.zeebe.engine.api.EngineFactory;

/**
 * Creates the engine implementation for broker tests. Production code never sees the engine; the
 * application provides it, and tests provide it through this helper.
 */
public final class TestEngineFactory {

  private TestEngineFactory() {}

  public static EngineFactory create() {
    return new DefaultEngineFactory();
  }
}
