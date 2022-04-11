/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.apache.commons.lang3.StringUtils;

import java.time.OffsetDateTime;
import java.util.Optional;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor
@Builder
@Data
@FieldNameConstants(asEnum = true)
public class SettingsResponseDto {
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
