/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;

import javax.ws.rs.QueryParam;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EventCountSearchRequestDto {

  @QueryParam("searchTerm")
  String searchTerm;
  @QueryParam("orderBy")
  String orderBy;
  @QueryParam("sortOrder")
  SortOrder sortOrder;

}
