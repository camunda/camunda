/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize;

import java.time.OffsetDateTime;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor
@Builder
@Data
public class SettingsDto {

  private Boolean sharingEnabled;

  private OffsetDateTime lastModified;

  public Optional<Boolean> getSharingEnabled() {
    return Optional.ofNullable(sharingEnabled);
  }

  public enum Fields {
    sharingEnabled,
    lastModified
  }
}
