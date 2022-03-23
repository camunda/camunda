/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.configuration.process_part;

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
    return "ProcessPartDto{" +
      "start='" + start + '\'' +
      ", end='" + end + '\'' +
      '}';
  }
}
