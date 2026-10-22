/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dmn.impl;

import io.camunda.zeebe.dmn.ParsedDecisionOutput;
import org.jspecify.annotations.Nullable;

public class ParsedDmnScalaDecisionOutput implements ParsedDecisionOutput {

  private final String id;
  private final @Nullable String name;

  public ParsedDmnScalaDecisionOutput(final String id, final @Nullable String name) {
    this.id = id;
    this.name = name;
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public @Nullable String getName() {
    return name;
  }
}
