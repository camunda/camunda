/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.management.dto;

import java.util.Objects;

public class GetBackupStateResponseDto {

  private BackupStateDto state;

  public GetBackupStateResponseDto() {
  }

  public GetBackupStateResponseDto(BackupStateDto state) {
    this.state = state;
  }

  public BackupStateDto getState() {
    return state;
  }

  public GetBackupStateResponseDto setState(BackupStateDto state) {
    this.state = state;
    return this;
  }

  @Override public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    GetBackupStateResponseDto that = (GetBackupStateResponseDto) o;
    return state == that.state;
  }

  @Override public int hashCode() {
    return Objects.hash(state);
  }
}
