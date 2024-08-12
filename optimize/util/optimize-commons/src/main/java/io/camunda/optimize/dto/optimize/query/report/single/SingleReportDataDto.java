/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.SingleReportConfigurationDto;
import io.camunda.optimize.service.util.TenantListHandlingUtil;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
public abstract class SingleReportDataDto implements ReportDataDto {

  @Getter @Setter @Builder.Default
  private SingleReportConfigurationDto configuration = new SingleReportConfigurationDto();

  @Getter @Setter @Builder.Default @Valid
  private List<ReportDataDefinitionDto> definitions = new ArrayList<>();

  @JsonIgnore
  public Optional<ReportDataDefinitionDto> getFirstDefinition() {
    return definitions.stream().findFirst();
  }

  @JsonIgnore
  public String getDefinitionKey() {
    return getDefinitions().stream().findFirst().map(ReportDataDefinitionDto::getKey).orElse(null);
  }

  @JsonIgnore
  public List<String> getDefinitionVersions() {
    return getDefinitions().stream()
        .findFirst()
        .map(ReportDataDefinitionDto::getVersions)
        .orElse(Collections.emptyList());
  }

  @JsonIgnore
  public String getDefinitionName() {
    return getDefinitions().stream().findFirst().map(ReportDataDefinitionDto::getName).orElse(null);
  }

  @JsonIgnore
  public List<String> getTenantIds() {
    return getFirstDefinition()
        .map(
            definition ->
                TenantListHandlingUtil.sortAndReturnTenantIdList(definition.getTenantIds()))
        // this is a special case as in case there is no definition or in case the source list is
        // indeed a null reference
        // this should get forwarded as such as the tenant logic handles both cases differently
        .orElse(null);
  }

  @JsonIgnore
  public void setTenantIds(final List<String> tenantIds) {
    if (definitions.isEmpty()) {
      definitions.add(new ReportDataDefinitionDto());
    }
    definitions.get(0).setTenantIds(tenantIds);
  }

  @JsonIgnore
  public abstract List<ViewProperty> getViewProperties();

  public static final class Fields {

    public static final String configuration = "configuration";
    public static final String definitions = "definitions";
  }
}
