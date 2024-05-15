/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize;

import java.time.OffsetDateTime;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor
@Builder
@Data
@FieldNameConstants(asEnum = true)
public class SettingsDto {

  private Boolean sharingEnabled;

  private OffsetDateTime lastModified;

  public Optional<Boolean> getSharingEnabled() {
    return Optional.ofNullable(sharingEnabled);
  }
}
