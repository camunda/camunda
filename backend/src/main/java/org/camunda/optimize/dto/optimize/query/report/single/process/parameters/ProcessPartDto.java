/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.parameters;

public class ProcessPartDto {

  protected String start;
  protected String end;

  public String getStart() {
    return start;
  }

  public void setStart(String start) {
    this.start = start;
  }

  public String getEnd() {
    return end;
  }

  public void setEnd(String end) {
    this.end = end;
  }

  public String createCommandKey() {
    return "processPart";
  }

  @Override
  public String toString() {
    return "ProcessPartDto{" +
      "start='" + start + '\'' +
      ", end='" + end + '\'' +
      '}';
  }
}
