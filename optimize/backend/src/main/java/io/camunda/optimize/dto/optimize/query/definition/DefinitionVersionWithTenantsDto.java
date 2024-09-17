/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.definition;

import static java.util.Comparator.naturalOrder;

import io.camunda.optimize.dto.optimize.SimpleDefinitionDto;
import io.camunda.optimize.dto.optimize.TenantDto;
import java.util.Comparator;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;

@Data
@EqualsAndHashCode(callSuper = true)
public class DefinitionVersionWithTenantsDto extends SimpleDefinitionDto {

  @NonNull private String version;
  private String versionTag;
  @NonNull private List<TenantDto> tenants;

  public DefinitionVersionWithTenantsDto(
      @NonNull String version, String versionTag, @NonNull List<TenantDto> tenants) {
    this.version = version;
    this.versionTag = versionTag;
    this.tenants = tenants;
  }

  protected DefinitionVersionWithTenantsDto() {}

  public void sort() {
    tenants.sort(Comparator.comparing(TenantDto::getId, Comparator.nullsFirst(naturalOrder())));
  }
}
