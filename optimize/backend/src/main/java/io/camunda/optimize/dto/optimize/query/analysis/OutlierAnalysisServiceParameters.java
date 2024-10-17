/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.analysis;

import java.time.ZoneId;

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
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $processDefinitionParametersDto = getProcessDefinitionParametersDto();
    result =
        result * PRIME
            + ($processDefinitionParametersDto == null
                ? 43
                : $processDefinitionParametersDto.hashCode());
    final Object $zoneId = getZoneId();
    result = result * PRIME + ($zoneId == null ? 43 : $zoneId.hashCode());
    final Object $userId = getUserId();
    result = result * PRIME + ($userId == null ? 43 : $userId.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof OutlierAnalysisServiceParameters)) {
      return false;
    }
    final OutlierAnalysisServiceParameters<?> other = (OutlierAnalysisServiceParameters<?>) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$processDefinitionParametersDto = getProcessDefinitionParametersDto();
    final Object other$processDefinitionParametersDto = other.getProcessDefinitionParametersDto();
    if (this$processDefinitionParametersDto == null
        ? other$processDefinitionParametersDto != null
        : !this$processDefinitionParametersDto.equals(other$processDefinitionParametersDto)) {
      return false;
    }
    final Object this$zoneId = getZoneId();
    final Object other$zoneId = other.getZoneId();
    if (this$zoneId == null ? other$zoneId != null : !this$zoneId.equals(other$zoneId)) {
      return false;
    }
    final Object this$userId = getUserId();
    final Object other$userId = other.getUserId();
    if (this$userId == null ? other$userId != null : !this$userId.equals(other$userId)) {
      return false;
    }
    return true;
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
