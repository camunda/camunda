/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.deployment.model.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

public final class YamlCase {
  @JsonProperty("case")
  private String condition = "";

  @JsonProperty("goto")
  private String next = "";

  @JsonProperty("default")
  private String defaultCase;

  public String getCondition() {
    return condition;
  }

  public void setCondition(final String condition) {
    this.condition = condition;
  }

  public String getNext() {
    return next;
  }

  public void setNext(final String next) {
    this.next = next;
  }

  public String getDefaultCase() {
    return defaultCase;
  }

  public void setDefaultCase(final String defaultCase) {
    this.defaultCase = defaultCase;
  }
}
