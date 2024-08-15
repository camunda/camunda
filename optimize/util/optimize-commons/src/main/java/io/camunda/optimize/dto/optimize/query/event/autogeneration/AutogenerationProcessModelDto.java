/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.event.autogeneration;

import io.camunda.optimize.dto.optimize.query.event.process.EventMappingDto;
import java.util.Map;

public class AutogenerationProcessModelDto {

  private String xml;
  private Map<String, EventMappingDto> mappings;

  public AutogenerationProcessModelDto(
      final String xml, final Map<String, EventMappingDto> mappings) {
    this.xml = xml;
    this.mappings = mappings;
  }

  public AutogenerationProcessModelDto() {}

  public String getXml() {
    return xml;
  }

  public Map<String, EventMappingDto> getMappings() {
    return mappings;
  }

  public static AutogenerationProcessModelDtoBuilder builder() {
    return new AutogenerationProcessModelDtoBuilder();
  }

  public static class AutogenerationProcessModelDtoBuilder {

    private String xml;
    private Map<String, EventMappingDto> mappings;

    AutogenerationProcessModelDtoBuilder() {}

    public AutogenerationProcessModelDtoBuilder xml(final String xml) {
      this.xml = xml;
      return this;
    }

    public AutogenerationProcessModelDtoBuilder mappings(
        final Map<String, EventMappingDto> mappings) {
      this.mappings = mappings;
      return this;
    }

    public AutogenerationProcessModelDto build() {
      return new AutogenerationProcessModelDto(xml, mappings);
    }

    @Override
    public String toString() {
      return "AutogenerationProcessModelDto.AutogenerationProcessModelDtoBuilder(xml="
          + xml
          + ", mappings="
          + mappings
          + ")";
    }
  }
}
