/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Index template representation. You can read more about index templates <a
 * href="https://www.elastic.co/guide/en/elasticsearch/reference/current/index-templates.html">here</a>.
 */
@JsonInclude(Include.NON_EMPTY)
public record Template(
    @JsonProperty("index_patterns") List<String> patterns,
    @JsonProperty("composed_of") List<String> composedOf,
    TemplateProperty template,
    Long priority,
    Long version) {

  public Template withTemplatePriority(final Long priority) {
    return new Template(patterns, composedOf, template, priority, version);
  }

  @JsonInclude(Include.NON_EMPTY)
  public record TemplateProperty(
      Map<String, Object> aliases, Map<String, Object> settings, Map<String, Object> mappings) {}
}
