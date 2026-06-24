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

public abstract class SingleReportDataDto implements ReportDataDto {

  private SingleReportConfigurationDto configuration = new SingleReportConfigurationDto();

  @Valid private List<ReportDataDefinitionDto> definitions = new ArrayList<>();

  public SingleReportDataDto(
      final SingleReportConfigurationDto configuration,
      @Valid final List<ReportDataDefinitionDto> definitions) {
    this.configuration = configuration;
    this.definitions = definitions;
  }

  protected SingleReportDataDto() {}

  protected SingleReportDataDto(final SingleReportDataDtoBuilder<?, ?> b) {
    if (b.configurationSet) {
      configuration = b.configurationValue;
    } else {
      configuration = defaultConfiguration();
    }
    if (b.definitionsSet) {
      definitions = b.definitionsValue;
    } else {
      definitions = defaultDefinitions();
    }
  }

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

  public SingleReportConfigurationDto getConfiguration() {
    return configuration;
  }

  public void setConfiguration(final SingleReportConfigurationDto configuration) {
    this.configuration = configuration;
  }

  public @Valid List<ReportDataDefinitionDto> getDefinitions() {
    return definitions;
  }

  public void setDefinitions(@Valid final List<ReportDataDefinitionDto> definitions) {
    this.definitions = definitions;
  }

  private static SingleReportConfigurationDto defaultConfiguration() {
    return new SingleReportConfigurationDto();
  }

  @Valid
  private static List<ReportDataDefinitionDto> defaultDefinitions() {
    return new ArrayList<>();
  }

  @SuppressWarnings("checkstyle:ConstantName")
  public static final class Fields {

    public static final String configuration = "configuration";
    public static final String definitions = "definitions";
  }

  public abstract static class SingleReportDataDtoBuilder<
      C extends SingleReportDataDto, B extends SingleReportDataDtoBuilder<C, B>> {

    private SingleReportConfigurationDto configurationValue;
    private boolean configurationSet;
    private @Valid List<ReportDataDefinitionDto> definitionsValue;
    private boolean definitionsSet;

    public B configuration(final SingleReportConfigurationDto configuration) {
      configurationValue = configuration;
      configurationSet = true;
      return self();
    }

    public B definitions(@Valid final List<ReportDataDefinitionDto> definitions) {
      definitionsValue = definitions;
      definitionsSet = true;
      return self();
    }

    protected abstract B self();

    public abstract C build();

    @Override
    public String toString() {
      return "SingleReportDataDto.SingleReportDataDtoBuilder(configuration$value="
          + configurationValue
          + ", definitionsValue="
          + definitionsValue
          + ")";
    }
  }
}
