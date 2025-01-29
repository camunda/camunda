/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.api.rest.v1.entities;

public class Risk {
  Precedence precedence;
  String classification;

  public Risk(Precedence precedence, String classification) {
    this.precedence = precedence;
    this.classification = classification;
  }

  public Precedence getPrecedence() {
    return precedence;
  }

  public void setPrecedence(Precedence precedence) {
    this.precedence = precedence;
  }

  public String getClassification() {
    return classification;
  }

  public void setClassification(String classification) {
    this.classification = classification;
  }
}
