/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.rest.pagination;

import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.QueryParam;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import static org.camunda.optimize.service.db.DatabaseConstants.MAX_RESPONSE_SIZE_LIMIT;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaginationScrollableRequestDto {

  public static final String QUERY_LIMIT_PARAM = "limit";
  public static final String QUERY_SCROLL_ID_PARAM = "searchRequestId";
  public static final String QUERY_SCROLL_TIMEOUT_PARAM = "paginationTimeout";

  @QueryParam(QUERY_LIMIT_PARAM)
  @Min(0)
  @DefaultValue("1000")
  @Max(MAX_RESPONSE_SIZE_LIMIT)
  protected Integer limit;

  @QueryParam(QUERY_SCROLL_ID_PARAM)
  protected String scrollId;

  @QueryParam(QUERY_SCROLL_TIMEOUT_PARAM)
  @Min(60)
  @DefaultValue("120")
  protected Integer scrollTimeout;
}
