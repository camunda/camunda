/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.analysis;

import java.time.ZoneId;
import java.util.Objects;

public class OutlierAnalysisServiceParameters<T extends ProcessDefinitionParametersDto> {

  private T processDefinitionParametersDto;
  private ZoneId zoneId;
  private String userId;

  public OutlierAnalysisServiceParameters(
      final T processDefinitionParametersDto, final ZoneId zoneId, final String userId) {
    this.processDefinitionParametersDto = processDefinitionParametersDto;
    this.zoneId = zoneId;
    this.userId = userId;
  }

  public T getProcessDefinitionParametersDto() {
    return processDefinitionParametersDto;
  }

  public void setProcessDefinitionParametersDto(final T processDefinitionParametersDto) {
    this.processDefinitionParametersDto = processDefinitionParametersDto;
  }

  public ZoneId getZoneId() {
    return zoneId;
  }

  public void setZoneId(final ZoneId zoneId) {
    this.zoneId = zoneId;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(final String userId) {
    this.userId = userId;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof OutlierAnalysisServiceParameters;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final OutlierAnalysisServiceParameters<?> that = (OutlierAnalysisServiceParameters<?>) o;
    return Objects.equals(processDefinitionParametersDto, that.processDefinitionParametersDto)
        && Objects.equals(zoneId, that.zoneId)
        && Objects.equals(userId, that.userId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(processDefinitionParametersDto, zoneId, userId);
  }

  @Override
  public String toString() {
    return "OutlierAnalysisServiceParameters(processDefinitionParametersDto="
        + getProcessDefinitionParametersDto()
        + ", zoneId="
        + getZoneId()
        + ", userId="
        + getUserId()
        + ")";
  }
}
