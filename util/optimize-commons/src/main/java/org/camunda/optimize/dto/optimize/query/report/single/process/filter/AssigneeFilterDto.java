/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.filter;

import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.IdentityLinkFilterDataDto;

import java.util.Arrays;
import java.util.List;

@NoArgsConstructor
public class AssigneeFilterDto extends ProcessFilterDto<IdentityLinkFilterDataDto> {

  public AssigneeFilterDto(final IdentityLinkFilterDataDto assigneeCandidateGroupFilterDataDto) {
    super(assigneeCandidateGroupFilterDataDto, FilterApplicationLevel.INSTANCE);
  }

  @Override
  public List<FilterApplicationLevel> validApplicationLevels() {
    return Arrays.asList(FilterApplicationLevel.INSTANCE, FilterApplicationLevel.VIEW);
  }

}
