/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.writer;

import com.google.common.collect.ImmutableSet;
import io.camunda.optimize.dto.optimize.TenantDto;
import java.util.List;
import java.util.Set;

public interface TenantWriter {

  Set<String> FIELDS_TO_UPDATE = ImmutableSet.of(TenantDto.Fields.name.name());

  void writeTenants(final List<TenantDto> tenantDtos);
}
