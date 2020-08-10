/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.filter;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.IdentityLinkFilterDataDto;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CandidateGroupFilterDto extends ProcessFilterDto<IdentityLinkFilterDataDto> {

  public CandidateGroupFilterDto(final IdentityLinkFilterDataDto assigneeCandidateGroupFilterDataDto) {
    super(assigneeCandidateGroupFilterDataDto);
  }

}
