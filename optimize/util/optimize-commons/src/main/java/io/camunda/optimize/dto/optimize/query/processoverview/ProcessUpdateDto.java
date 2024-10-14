/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.processoverview;

import io.camunda.optimize.dto.optimize.OptimizeDto;
import jakarta.validation.constraints.NotNull;

public class ProcessUpdateDto implements OptimizeDto {

  private String ownerId;
  @NotNull private ProcessDigestRequestDto processDigest;

  public ProcessUpdateDto(
      final String ownerId, @NotNull final ProcessDigestRequestDto processDigest) {
    this.ownerId = ownerId;
    this.processDigest = processDigest;
  }

  public ProcessUpdateDto() {}

  public String getOwnerId() {
    return ownerId;
  }

  public void setOwnerId(final String ownerId) {
    this.ownerId = ownerId;
  }

  public @NotNull ProcessDigestRequestDto getProcessDigest() {
    return processDigest;
  }

  public void setProcessDigest(@NotNull final ProcessDigestRequestDto processDigest) {
    this.processDigest = processDigest;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ProcessUpdateDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $ownerId = getOwnerId();
    result = result * PRIME + ($ownerId == null ? 43 : $ownerId.hashCode());
    final Object $processDigest = getProcessDigest();
    result = result * PRIME + ($processDigest == null ? 43 : $processDigest.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof ProcessUpdateDto)) {
      return false;
    }
    final ProcessUpdateDto other = (ProcessUpdateDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$ownerId = getOwnerId();
    final Object other$ownerId = other.getOwnerId();
    if (this$ownerId == null ? other$ownerId != null : !this$ownerId.equals(other$ownerId)) {
      return false;
    }
    final Object this$processDigest = getProcessDigest();
    final Object other$processDigest = other.getProcessDigest();
    if (this$processDigest == null
        ? other$processDigest != null
        : !this$processDigest.equals(other$processDigest)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "ProcessUpdateDto(ownerId="
        + getOwnerId()
        + ", processDigest="
        + getProcessDigest()
        + ")";
  }
}
