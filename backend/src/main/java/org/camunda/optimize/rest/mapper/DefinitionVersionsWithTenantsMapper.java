/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest.mapper;

import lombok.experimental.UtilityClass;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionAvailableVersionsWithTenants;
import org.camunda.optimize.dto.optimize.rest.TenantRestDto;
import org.camunda.optimize.dto.optimize.rest.definition.DefinitionVersionsWithTenantsRestDto;
import org.camunda.optimize.dto.optimize.rest.definition.DefinitionWithTenantsRestDto;

import java.util.List;
import java.util.stream.Collectors;

@UtilityClass
public class DefinitionVersionsWithTenantsMapper {
  public static List<DefinitionVersionsWithTenantsRestDto> mapToDefinitionVersionsWithTenantsRestDto(
    final List<DefinitionAvailableVersionsWithTenants> processDefinitionVersionsWithTenants) {
    return processDefinitionVersionsWithTenants
      .stream()
      .map(entry -> new DefinitionVersionsWithTenantsRestDto(
        entry.getKey(),
        entry.getName(),
        entry.getVersions()
          .stream()
          .map(definitionWithTenants -> new DefinitionWithTenantsRestDto(
            definitionWithTenants.getVersion(),
            definitionWithTenants.getVersionTag(),
            definitionWithTenants.getTenants()
              .stream()
              .map(tenantDto -> new TenantRestDto(tenantDto.getId(), tenantDto.getName()))
              .collect(Collectors.toList())
          ))
          .collect(Collectors.toList())
      ))
      .collect(Collectors.toList());
  }
}
