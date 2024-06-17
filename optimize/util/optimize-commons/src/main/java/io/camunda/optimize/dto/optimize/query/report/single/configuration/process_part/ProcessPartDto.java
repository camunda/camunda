/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.configuration.process_part;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
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
}
