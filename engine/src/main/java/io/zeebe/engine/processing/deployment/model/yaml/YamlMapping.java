/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.deployment.model.yaml;

public final class YamlMapping {
  private static final String DEFAULT_MAPPING = "$";

  private String source = DEFAULT_MAPPING;
  private String target = DEFAULT_MAPPING;

  public String getSource() {
    return source;
  }

  public void setSource(final String source) {
    this.source = source;
  }

  public String getTarget() {
    return target;
  }

  public void setTarget(final String target) {
    this.target = target;
  }
}
