/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.opensearch.dto;

import java.util.List;
import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.ObjectBuilderDeserializer;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.opensearch.indices.put_index_template.IndexTemplateMapping;
import org.opensearch.client.util.ObjectBuilder;

/**
 * Index template representation. You can read more about index templates <a
 * href="https://opensearch.org/docs/latest/im-plugin/index-templates/">here</a>.
 */
public record Template(
    Long version,
    Long priority,
    List<String> composedOf,
    List<String> indexPatterns,
    IndexTemplateMapping template) {

  public static final JsonpDeserializer<Template> DESERIALIZER =
      ObjectBuilderDeserializer.lazy(Template.Builder::new, Template::setupDeserializer);

  private static void setupDeserializer(final ObjectDeserializer<Builder> deserializer) {
    deserializer.add(Builder::version, JsonpDeserializer.longDeserializer(), "version");
    deserializer.add(Builder::priority, JsonpDeserializer.longDeserializer(), "priority");
    deserializer.add(
        Builder::composedOf,
        JsonpDeserializer.arrayDeserializer(JsonpDeserializer.stringDeserializer()),
        "composed_of");
    deserializer.add(
        Builder::indexPatterns,
        JsonpDeserializer.arrayDeserializer(JsonpDeserializer.stringDeserializer()),
        "index_patterns");
    deserializer.add(Builder::template, IndexTemplateMapping._DESERIALIZER, "template");
  }

  static class Builder implements ObjectBuilder<Template> {
    Long version;
    Long priority;
    List<String> composedOf;
    List<String> indexPatterns;
    IndexTemplateMapping template;

    Template.Builder version(final Long version) {
      this.version = version;
      return this;
    }

    Template.Builder priority(final Long priority) {
      this.priority = priority;
      return this;
    }

    Template.Builder composedOf(final List<String> composedOf) {
      this.composedOf = composedOf;
      return this;
    }

    Template.Builder indexPatterns(final List<String> indexPatterns) {
      this.indexPatterns = indexPatterns;
      return this;
    }

    Template.Builder template(final IndexTemplateMapping template) {
      this.template = template;
      return this;
    }

    @Override
    public Template build() {
      return new Template(version, priority, composedOf, indexPatterns, template);
    }
  }
}
