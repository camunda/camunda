/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.process.filter;

import io.camunda.optimize.dto.optimize.query.report.single.process.filter.data.IdentityLinkFilterDataDto;
import java.util.Arrays;
import java.util.List;

public class CandidateGroupFilterDto extends ProcessFilterDto<IdentityLinkFilterDataDto> {

  public CandidateGroupFilterDto(
      final IdentityLinkFilterDataDto assigneeCandidateGroupFilterDataDto) {
    super(assigneeCandidateGroupFilterDataDto, FilterApplicationLevel.INSTANCE);
  }

  public CandidateGroupFilterDto() {}

  @Override
  public List<FilterApplicationLevel> validApplicationLevels() {
    return Arrays.asList(FilterApplicationLevel.INSTANCE, FilterApplicationLevel.VIEW);
  }
}
