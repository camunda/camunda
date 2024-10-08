/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.migrate313to86.indices.db;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.OffsetDateTime;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.apache.commons.lang3.StringUtils;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor
@Builder
@Data
@FieldNameConstants(asEnum = true)
public class OldSettingsDto {

  private Boolean metadataTelemetryEnabled;
  private Boolean sharingEnabled;

  private String lastModifier;
  private OffsetDateTime lastModified;

  @JsonIgnore
  public boolean isTelemetryManuallyConfirmed() {
    return getMetadataTelemetryEnabled().isPresent() && !StringUtils.isEmpty(lastModifier);
  }

  public Optional<Boolean> getMetadataTelemetryEnabled() {
    return Optional.ofNullable(metadataTelemetryEnabled);
  }

  public Optional<Boolean> getSharingEnabled() {
    return Optional.ofNullable(sharingEnabled);
  }
}
