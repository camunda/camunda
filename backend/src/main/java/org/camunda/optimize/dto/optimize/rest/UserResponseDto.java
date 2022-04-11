/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.rest;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.UserDto;

import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class UserResponseDto {
  @JsonUnwrapped
  private UserDto userDto;
  private List<AuthorizationType> authorizations;
}
