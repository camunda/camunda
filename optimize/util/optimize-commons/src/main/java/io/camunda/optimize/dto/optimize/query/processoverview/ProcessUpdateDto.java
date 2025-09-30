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
import java.util.Objects;

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
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ProcessUpdateDto that = (ProcessUpdateDto) o;
    return Objects.equals(ownerId, that.ownerId)
        && Objects.equals(processDigest, that.processDigest);
  }

  @Override
  public int hashCode() {
    return Objects.hash(ownerId, processDigest);
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
