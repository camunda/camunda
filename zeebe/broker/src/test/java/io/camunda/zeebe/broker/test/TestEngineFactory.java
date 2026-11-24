/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.test;

import io.camunda.zeebe.engine.api.EngineFactory;
import java.util.ServiceLoader;

/**
 * Creates the engine implementation for broker tests. The engine is only on the test runtime
 * classpath, so tests cannot compile against its internals and find the implementation through
 * {@link ServiceLoader}.
 */
public final class TestEngineFactory {

  private TestEngineFactory() {}

  public static EngineFactory create() {
    return ServiceLoader.load(EngineFactory.class).findFirst().orElseThrow();
  }
}
