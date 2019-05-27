/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.result;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.camunda.optimize.dto.optimize.query.report.single.result.LimitedResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.MapResultEntryDto;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Data
@EqualsAndHashCode(callSuper = true)
public abstract class ProcessReportMapResult<T extends Comparable> extends ProcessReportResultDto
  implements LimitedResultDto {
  private List<MapResultEntryDto<T>> data = new ArrayList<>();
  private Boolean isComplete = true;

  public Optional<MapResultEntryDto<T>> getDataEntryForKey(final String key) {
    return data.stream().filter(entry -> key.equals(entry.getKey())).findFirst();
  }
}
