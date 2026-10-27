/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.configuration.process_part;

public class ProcessPartDto {

  protected String start;
  protected String end;

  public String createCommandKey() {
    return "processPart";
  }

  @Override
  public String toString() {
    return "ProcessPartDto{" + "start='" + start + '\'' + ", end='" + end + '\'' + '}';
  }

  public String getStart() {
    return start;
  }

  public void setStart(final String start) {
    this.start = start;
  }

  public String getEnd() {
    return end;
  }

  public void setEnd(final String end) {
    this.end = end;
  }
}
