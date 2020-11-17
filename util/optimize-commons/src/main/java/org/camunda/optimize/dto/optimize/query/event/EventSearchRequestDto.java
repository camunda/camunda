/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.event;

import com.google.common.collect.ImmutableList;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.query.event.process.EventDto;
import org.camunda.optimize.dto.optimize.rest.pagination.PaginationRequestDto;
import org.camunda.optimize.dto.optimize.rest.sorting.SortRequestDto;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.QueryParam;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventSearchRequestDto {

  public static final List<String> sortableFields = ImmutableList.of(
    EventDto.Fields.group.toLowerCase(),
    EventDto.Fields.source.toLowerCase(),
    EventDto.Fields.eventName.toLowerCase(),
    EventDto.Fields.traceId.toLowerCase(),
    EventDto.Fields.timestamp.toLowerCase()
  );

  @QueryParam("searchTerm")
  private String searchTerm;
  @BeanParam
  @Valid
  @NotNull
  private SortRequestDto sortRequestDto;
  @BeanParam
  @Valid
  @NotNull
  private PaginationRequestDto paginationRequestDto;

}
