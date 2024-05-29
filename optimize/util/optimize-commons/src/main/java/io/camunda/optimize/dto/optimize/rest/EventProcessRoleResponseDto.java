/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.dto.optimize.rest;

import io.camunda.optimize.dto.optimize.IdentityWithMetadataResponseDto;
import io.camunda.optimize.dto.optimize.query.event.process.EventProcessRoleRequestDto;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventProcessRoleResponseDto
    extends EventProcessRoleRequestDto<IdentityWithMetadataResponseDto> {

  public EventProcessRoleResponseDto(final IdentityWithMetadataResponseDto identity) {
    super(identity);
  }
}
