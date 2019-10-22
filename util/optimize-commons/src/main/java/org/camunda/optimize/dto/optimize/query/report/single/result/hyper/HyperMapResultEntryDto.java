/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.result.hyper;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.sorting.SortingDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;

import java.util.List;
import java.util.Optional;

@EqualsAndHashCode
public class HyperMapResultEntryDto {

  // @formatter:off
  @NonNull @Getter @Setter private String key;
  @Setter private String label;
  // @formatter:on

  @JsonIgnore
  // the List<MapResultEntryDto> value field be provided using the getter of this field
  private ReportMapResultDto resultValueHolder;

  @JsonProperty("value")
  public List<MapResultEntryDto> getValue() {
    return resultValueHolder.getData();
  }

  protected HyperMapResultEntryDto() {
  }

  public Optional<MapResultEntryDto> getDataEntryForKey(final String key) {
    return getValue().stream().filter(entry -> key.equals(entry.getKey())).findFirst();
  }

  public HyperMapResultEntryDto(@NonNull final String key, final List<MapResultEntryDto> value) {
    this.key = key;
    setValue(value);
  }

  public HyperMapResultEntryDto(@NonNull final String key, final List<MapResultEntryDto> value, String label) {
    this.key = key;
    setValue(value);
    this.label = label;
  }

  public void setValue(final List<MapResultEntryDto> value) {
    this.resultValueHolder = new ReportMapResultDto();
    this.resultValueHolder.setData(value);
  }

  public String getLabel() {
    return label != null && !label.isEmpty() ? label : key;
  }

  public void sortResultData(final SortingDto sorting, final VariableType keyType) {
    this.resultValueHolder.sortResultData(sorting, keyType);
  }
}
