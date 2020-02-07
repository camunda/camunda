/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.rest;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.IdentityWithMetadataDto;
import org.camunda.optimize.dto.optimize.query.event.EventProcessRoleDto;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@FieldNameConstants
public class EventProcessRoleRestDto extends EventProcessRoleDto<IdentityWithMetadataDto> {

  public EventProcessRoleRestDto(final IdentityWithMetadataDto identity) {
    super(identity);
  }

}
