/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.rest;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Data
public class BackupRequestDto {
  @NotBlank
  //  max. 3996 chars to avoid too_long_frame_exception (snapshot name must not exceed 4096 chars, minus 100 chars for rest of
  //  snapshot name templates)
  @Pattern(regexp = "((?![A-Z \"*\\\\<|,>\\/?_]).){0,3996}$", message =
    "BackupId must be less than 3996 characters and must not contain any uppercase letters or any of [ , \", *, \\, <, |, ,, >," +
      " /, ?, _].")
  private String backupId;
}
