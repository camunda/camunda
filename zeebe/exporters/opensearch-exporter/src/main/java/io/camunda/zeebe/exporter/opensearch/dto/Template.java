/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.opensearch.dto;

import static java.util.Optional.ofNullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Index template representation. You can read more about index templates <a
 * href="https://opensearch.org/docs/latest/im-plugin/index-templates/">here</a>.
 */
@JsonInclude(Include.NON_EMPTY)
public record Template(
    @JsonProperty("index_patterns") List<String> patterns,
    @JsonProperty("composed_of") List<String> composedOf,
    TemplateProperty template,
    Long priority,
    Long version) {

  @JsonInclude(Include.NON_EMPTY)
  public record TemplateProperty(
      Map<String, Object> aliases, Map<String, Object> settings, Map<String, Object> mappings) {
    public static TemplateProperty mutableCopyOf(final TemplateProperty templateProperty) {
      if (templateProperty == null) {
        return new TemplateProperty(new HashMap<>(), new HashMap<>(), new HashMap<>());
      }
      return new TemplateProperty(
          ofNullable(templateProperty.aliases).map(HashMap::new).orElseGet(HashMap::new),
          ofNullable(templateProperty.settings).map(HashMap::new).orElseGet(HashMap::new),
          ofNullable(templateProperty.mappings).map(HashMap::new).orElseGet(HashMap::new));
    }
  }

  public static final class MutableCopyBuilder {
    private List<String> patterns;
    private List<String> composedOf;
    private TemplateProperty template;
    private Long priority;
    private Long version;

    private MutableCopyBuilder() {}

    public static MutableCopyBuilder copyOf(final Template template) {
      final MutableCopyBuilder builder = new MutableCopyBuilder();
      builder.patterns =
          ofNullable(template.patterns).map(ArrayList::new).orElseGet(ArrayList::new);
      builder.composedOf =
          ofNullable(template.composedOf).map(ArrayList::new).orElseGet(ArrayList::new);
      builder.template = TemplateProperty.mutableCopyOf(template.template);
      builder.priority = template.priority;
      builder.version = template.version;
      return builder;
    }

    public MutableCopyBuilder updatePatterns(final Consumer<List<String>> patternsConsumer) {
      patternsConsumer.accept(patterns);
      return this;
    }

    public MutableCopyBuilder updateComposedOf(final Consumer<List<String>> composedOfConsumer) {
      composedOfConsumer.accept(composedOf);
      return this;
    }

    public MutableCopyBuilder updateAliases(final Consumer<Map<String, Object>> aliasesConsumer) {
      aliasesConsumer.accept(template.aliases);
      return this;
    }

    public MutableCopyBuilder withPriority(final Long priority) {
      this.priority = priority;
      return this;
    }

    public Template build() {
      return new Template(patterns, composedOf, template, priority, version);
    }
  }
}
