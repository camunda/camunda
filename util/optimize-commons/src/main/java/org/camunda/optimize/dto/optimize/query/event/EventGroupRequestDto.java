/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.event;

import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.QueryParam;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventGroupRequestDto {

  @QueryParam("searchTerm")
  private String searchTerm;

  @QueryParam("limit")
  @NotNull
  private int limit;

  public void validateRequest() {
    if (StringUtils.isEmpty(searchTerm)) {
      searchTerm = null;
    }
  }
}
