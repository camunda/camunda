/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.parameters;

import org.camunda.optimize.dto.optimize.query.report.single.sorting.SortingDto;

import java.util.Optional;

public class ProcessParametersDto {
  // nullable
  private ProcessPartDto processPart;
  // nullable
  private SortingDto sorting;

  public ProcessParametersDto() {
  }

  public ProcessParametersDto(ProcessPartDto processPart) {
    this(processPart, null);
  }

  public ProcessParametersDto(SortingDto sorting) {
    this(null, sorting);
  }

  public ProcessParametersDto(ProcessPartDto processPart, SortingDto sorting) {
    this.processPart = processPart;
    this.sorting = sorting;
  }

  public ProcessPartDto getProcessPart() {
    return processPart;
  }

  public Optional<SortingDto> getSorting() {
    return Optional.ofNullable(sorting);
  }

  public void setProcessPart(ProcessPartDto processPart) {
    this.processPart = processPart;
  }

  public void setSorting(SortingDto sorting) {
    this.sorting = sorting;
  }

  @Override
  public String toString() {
    return "ProcessParametersDto{" +
      "processPart=" + processPart +
      ", sorting=" + sorting +
      '}';
  }
}
