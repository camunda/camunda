/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.configuration;

/**
 * Configuration to load a single extra filter. The {@link #className} property is required, and
 * must be a fully qualified name referring to an implementation of {@link jakarta.servlet.Filter}.
 *
 * <p>Optionally, a {@link #jarPath} can be supplied, and the class will be looked up there. Note
 * that the JAR is loaded within an isolated class loader to avoid dependency conflicts, so you must
 * make sure that all dependencies are available in the JAR, or via the gateway itself.
 */
public final class FilterCfg extends BaseExternalCodeCfg {

  @Override
  public String toString() {
    return "FilterCfg{" + ", jarPath='" + jarPath + '\'' + ", className='" + className + '\'' + '}';
  }
}
