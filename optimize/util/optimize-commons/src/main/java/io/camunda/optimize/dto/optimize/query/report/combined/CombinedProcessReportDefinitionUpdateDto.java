/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.combined;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionUpdateDto;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class CombinedProcessReportDefinitionUpdateDto extends ReportDefinitionUpdateDto {

  protected CombinedReportDataDto data;

  public CombinedProcessReportDefinitionUpdateDto() {}

  public CombinedReportDataDto getData() {
    return data;
  }

  public void setData(final CombinedReportDataDto data) {
    this.data = data;
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof CombinedProcessReportDefinitionUpdateDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = super.hashCode();
    final Object $data = getData();
    result = result * PRIME + ($data == null ? 43 : $data.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof CombinedProcessReportDefinitionUpdateDto)) {
      return false;
    }
    final CombinedProcessReportDefinitionUpdateDto other =
        (CombinedProcessReportDefinitionUpdateDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final Object this$data = getData();
    final Object other$data = other.getData();
    if (this$data == null ? other$data != null : !this$data.equals(other$data)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "CombinedProcessReportDefinitionUpdateDto(data=" + getData() + ")";
  }
}
