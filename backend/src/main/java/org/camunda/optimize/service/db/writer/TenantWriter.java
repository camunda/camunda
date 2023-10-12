/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.writer;

import com.google.common.collect.ImmutableSet;
import org.camunda.optimize.dto.optimize.TenantDto;

import java.util.List;
import java.util.Set;

public interface TenantWriter {

  Set<String> FIELDS_TO_UPDATE = ImmutableSet.of(TenantDto.Fields.name.name());

  void writeTenants(final List<TenantDto> tenantDtos);

}
