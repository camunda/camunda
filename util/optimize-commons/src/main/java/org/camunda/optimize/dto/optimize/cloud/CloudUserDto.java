/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.cloud;

import java.util.List;
import java.util.function.Supplier;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CloudUserDto {

  private String userId;
  private String name;
  private String email;
  private List<String> roles;

  public List<Supplier<String>> getSearchableDtoFields() {
    return List.of(this::getUserId, this::getName, this::getEmail);
  }
}
