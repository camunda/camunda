/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize;

import io.camunda.optimize.dto.optimize.datasource.DataSourceDto;
import io.camunda.optimize.dto.optimize.datasource.EngineDataSourceDto;
import io.camunda.optimize.dto.optimize.query.variable.DecisionVariableNameResponseDto;
import java.util.ArrayList;
import java.util.List;

public class DecisionDefinitionOptimizeDto extends DefinitionOptimizeResponseDto {

  private String dmn10Xml;
  private List<DecisionVariableNameResponseDto> inputVariableNames = new ArrayList<>();
  private List<DecisionVariableNameResponseDto> outputVariableNames = new ArrayList<>();

  public DecisionDefinitionOptimizeDto() {
    setType(DefinitionType.DECISION);
  }

  public DecisionDefinitionOptimizeDto(
      final String id,
      final EngineDataSourceDto dataSource,
      final String dmn10Xml,
      final List<DecisionVariableNameResponseDto> inputVariableNames,
      final List<DecisionVariableNameResponseDto> outputVariableNames) {
    super(id, dataSource);
    this.dmn10Xml = dmn10Xml;
    this.inputVariableNames = inputVariableNames;
    this.outputVariableNames = outputVariableNames;
  }

  public DecisionDefinitionOptimizeDto(
      final String id,
      final String key,
      final String version,
      final String versionTag,
      final String name,
      final DataSourceDto dataSource,
      final String tenantId,
      final String dmn10Xml,
      final boolean deleted,
      final List<DecisionVariableNameResponseDto> inputVariableNames,
      final List<DecisionVariableNameResponseDto> outputVariableNames) {
    super(
        id, key, version, versionTag, name, dataSource, tenantId, deleted, DefinitionType.DECISION);
    this.dmn10Xml = dmn10Xml;
    this.inputVariableNames = inputVariableNames;
    this.outputVariableNames = outputVariableNames;
  }

  public DecisionDefinitionOptimizeDto(
      final String dmn10Xml,
      final List<DecisionVariableNameResponseDto> inputVariableNames,
      final List<DecisionVariableNameResponseDto> outputVariableNames) {
    this.dmn10Xml = dmn10Xml;
    this.inputVariableNames = inputVariableNames;
    this.outputVariableNames = outputVariableNames;
  }

  public String getDmn10Xml() {
    return dmn10Xml;
  }

  public void setDmn10Xml(final String dmn10Xml) {
    this.dmn10Xml = dmn10Xml;
  }

  public List<DecisionVariableNameResponseDto> getInputVariableNames() {
    return inputVariableNames;
  }

  public void setInputVariableNames(
      final List<DecisionVariableNameResponseDto> inputVariableNames) {
    this.inputVariableNames = inputVariableNames;
  }

  public List<DecisionVariableNameResponseDto> getOutputVariableNames() {
    return outputVariableNames;
  }

  public void setOutputVariableNames(
      final List<DecisionVariableNameResponseDto> outputVariableNames) {
    this.outputVariableNames = outputVariableNames;
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof DecisionDefinitionOptimizeDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = super.hashCode();
    final Object $dmn10Xml = getDmn10Xml();
    result = result * PRIME + ($dmn10Xml == null ? 43 : $dmn10Xml.hashCode());
    final Object $inputVariableNames = getInputVariableNames();
    result = result * PRIME + ($inputVariableNames == null ? 43 : $inputVariableNames.hashCode());
    final Object $outputVariableNames = getOutputVariableNames();
    result = result * PRIME + ($outputVariableNames == null ? 43 : $outputVariableNames.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof DecisionDefinitionOptimizeDto)) {
      return false;
    }
    final DecisionDefinitionOptimizeDto other = (DecisionDefinitionOptimizeDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final Object this$dmn10Xml = getDmn10Xml();
    final Object other$dmn10Xml = other.getDmn10Xml();
    if (this$dmn10Xml == null ? other$dmn10Xml != null : !this$dmn10Xml.equals(other$dmn10Xml)) {
      return false;
    }
    final Object this$inputVariableNames = getInputVariableNames();
    final Object other$inputVariableNames = other.getInputVariableNames();
    if (this$inputVariableNames == null
        ? other$inputVariableNames != null
        : !this$inputVariableNames.equals(other$inputVariableNames)) {
      return false;
    }
    final Object this$outputVariableNames = getOutputVariableNames();
    final Object other$outputVariableNames = other.getOutputVariableNames();
    if (this$outputVariableNames == null
        ? other$outputVariableNames != null
        : !this$outputVariableNames.equals(other$outputVariableNames)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "DecisionDefinitionOptimizeDto(dmn10Xml="
        + getDmn10Xml()
        + ", inputVariableNames="
        + getInputVariableNames()
        + ", outputVariableNames="
        + getOutputVariableNames()
        + ")";
  }

  public static DecisionDefinitionOptimizeDtoBuilder builder() {
    return new DecisionDefinitionOptimizeDtoBuilder();
  }

  public static class DecisionDefinitionOptimizeDtoBuilder {

    private String id;
    private String key;
    private String version;
    private String versionTag;
    private String name;
    private DataSourceDto dataSource;
    private String tenantId;
    private String dmn10Xml;
    private boolean deleted;
    private List<DecisionVariableNameResponseDto> inputVariableNames;
    private List<DecisionVariableNameResponseDto> outputVariableNames;

    DecisionDefinitionOptimizeDtoBuilder() {}

    public DecisionDefinitionOptimizeDtoBuilder id(final String id) {
      this.id = id;
      return this;
    }

    public DecisionDefinitionOptimizeDtoBuilder key(final String key) {
      this.key = key;
      return this;
    }

    public DecisionDefinitionOptimizeDtoBuilder version(final String version) {
      this.version = version;
      return this;
    }

    public DecisionDefinitionOptimizeDtoBuilder versionTag(final String versionTag) {
      this.versionTag = versionTag;
      return this;
    }

    public DecisionDefinitionOptimizeDtoBuilder name(final String name) {
      this.name = name;
      return this;
    }

    public DecisionDefinitionOptimizeDtoBuilder dataSource(final DataSourceDto dataSource) {
      this.dataSource = dataSource;
      return this;
    }

    public DecisionDefinitionOptimizeDtoBuilder tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    public DecisionDefinitionOptimizeDtoBuilder dmn10Xml(final String dmn10Xml) {
      this.dmn10Xml = dmn10Xml;
      return this;
    }

    public DecisionDefinitionOptimizeDtoBuilder deleted(final boolean deleted) {
      this.deleted = deleted;
      return this;
    }

    public DecisionDefinitionOptimizeDtoBuilder inputVariableNames(
        final List<DecisionVariableNameResponseDto> inputVariableNames) {
      this.inputVariableNames = inputVariableNames;
      return this;
    }

    public DecisionDefinitionOptimizeDtoBuilder outputVariableNames(
        final List<DecisionVariableNameResponseDto> outputVariableNames) {
      this.outputVariableNames = outputVariableNames;
      return this;
    }

    public DecisionDefinitionOptimizeDto build() {
      return new DecisionDefinitionOptimizeDto(
          id,
          key,
          version,
          versionTag,
          name,
          dataSource,
          tenantId,
          dmn10Xml,
          deleted,
          inputVariableNames,
          outputVariableNames);
    }

    @Override
    public String toString() {
      return "DecisionDefinitionOptimizeDto.DecisionDefinitionOptimizeDtoBuilder(id="
          + id
          + ", key="
          + key
          + ", version="
          + version
          + ", versionTag="
          + versionTag
          + ", name="
          + name
          + ", dataSource="
          + dataSource
          + ", tenantId="
          + tenantId
          + ", dmn10Xml="
          + dmn10Xml
          + ", deleted="
          + deleted
          + ", inputVariableNames="
          + inputVariableNames
          + ", outputVariableNames="
          + outputVariableNames
          + ")";
    }
  }

  public enum Fields {
    dmn10Xml,
    inputVariableNames,
    outputVariableNames
  }
}
