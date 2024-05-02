/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.entities;

public class HitEntity {
  String index;

  String sourceAsString;

  public String getSourceAsString() {
    return sourceAsString;
  }

  public HitEntity setSourceAsString(String sourceAsString) {
    this.sourceAsString = sourceAsString;
    return this;
  }

  public String getIndex() {
    return index;
  }

  public HitEntity setIndex(String index) {
    this.index = index;
    return this;
  }
}
