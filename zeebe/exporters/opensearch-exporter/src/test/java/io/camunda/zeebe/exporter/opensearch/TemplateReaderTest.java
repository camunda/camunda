/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.opensearch;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.exporter.opensearch.OpensearchExporterConfiguration.IndexConfiguration;
import io.camunda.zeebe.exporter.opensearch.dto.Template;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.util.VersionUtil;
import java.util.Map;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.opensearch.client.opensearch._types.mapping.DynamicMapping;
import org.opensearch.client.opensearch._types.mapping.LongNumberProperty;
import org.opensearch.client.opensearch._types.mapping.ObjectProperty;
import org.opensearch.client.opensearch._types.mapping.Property;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import org.opensearch.client.opensearch.cluster.PutComponentTemplateRequest;
import org.opensearch.client.opensearch.indices.Alias;
import org.opensearch.client.opensearch.indices.PutIndexTemplateRequest;

@Execution(ExecutionMode.CONCURRENT)
final class TemplateReaderTest {
  private final IndexConfiguration config = new IndexConfiguration();
  private final TemplateReader templateReader = new TemplateReader(config);

  @Test
  void shouldReadComponentTemplate() {
    // when
    final Template template = templateReader.readComponentTemplate();

    // then component template settings set
    assertThat(template.template().settings().index().numberOfShards()).isEqualTo(1);
    assertThat(template.template().settings().index().numberOfReplicas()).isEqualTo(0);
    assertThat(template.template().settings().index().queries().cache().enabled()).isFalse();

    // then template settings not set
    assertThat(template.composedOf())
        .as("component template is not composed of anything")
        .isNullOrEmpty();
    assertThat(template.indexPatterns())
        .as(
            "component template has no search patterns as it's meant to be combined"
                + " with one or more index templates")
        .isNullOrEmpty();
    assertThat(template.template().aliases())
        .as("component template has no need for aliases")
        .isNullOrEmpty();
  }

  @Test
  void shouldGetPutComponentTemplateRequest() {
    // when
    final PutComponentTemplateRequest request =
        templateReader.getComponentTemplatePutRequest("test-template");

    // then
    assertThat(request.template().settings().index().numberOfShards()).isEqualTo(1);
    assertThat(request.template().settings().index().numberOfReplicas()).isEqualTo(0);
    assertThat(request.template().settings().index().queries().cache().enabled()).isFalse();

    final Map<String, Property> expectedProperties =
        Map.ofEntries(
            Map.entry(
                "position", Property.builder().long_(LongNumberProperty.builder().build()).build()),
            Map.entry(
                "sourceRecordPosition",
                Property.builder().long_(LongNumberProperty.builder().build()).build()),
            Map.entry(
                "key", Property.builder().long_(LongNumberProperty.builder().build()).build()),
            Map.entry("timestamp", Property.builder().date(d -> d).build()),
            Map.entry("intent", Property.builder().keyword(k -> k).build()),
            Map.entry("partitionId", Property.builder().integer(i -> i).build()),
            Map.entry("recordType", Property.builder().keyword(k -> k).build()),
            Map.entry("rejectionType", Property.builder().keyword(k -> k).build()),
            Map.entry("rejectionReason", Property.builder().text(t -> t).build()),
            Map.entry("valueType", Property.builder().keyword(k -> k).build()),
            Map.entry("brokerVersion", Property.builder().keyword(k -> k).build()),
            Map.entry("recordVersion", Property.builder().integer(i -> i).build()),
            Map.entry(
                "sequence", Property.builder().long_(LongNumberProperty.builder().build()).build()),
            Map.entry(
                "operationReference",
                Property.builder().long_(LongNumberProperty.builder().build()).build()),
            Map.entry(
                "batchOperationReference",
                Property.builder().long_(LongNumberProperty.builder().build()).build()),
            Map.entry(
                "agent",
                Property.builder()
                    .object(
                        o ->
                            o.properties(
                                "elementId",
                                Property.builder().keyword(k -> k.index(false)).build()))
                    .build()));

    assertThat(request.template().mappings())
        .as("component template should have mappings with properties")
        .hasFieldOrPropertyWithValue("dynamic", DynamicMapping.Strict)
        .hasFieldOrProperty("properties")
        .extracting(TypeMapping::properties)
        .asInstanceOf(InstanceOfAssertFactories.map(String.class, Property.class))
        .containsExactlyInAnyOrderEntriesOf(expectedProperties);
  }

  @Test
  void shouldSetNumberOfShardsInComponentTemplate() {
    // given
    config.setNumberOfShards(30);

    // then
    assertThat(
            templateReader.readComponentTemplate().template().settings().index().numberOfShards())
        .as("should have the default number of shards in template")
        .isEqualTo(1);

    // when
    final PutComponentTemplateRequest request =
        templateReader.getComponentTemplatePutRequest("test-template");

    // then
    assertThat(request.template().settings().index().numberOfShards())
        .as("request should have the configured number of shards")
        .isEqualTo(30);
  }

  @Test
  void shouldSetNumberOfReplicasInComponentTemplate() {
    // given
    config.setNumberOfReplicas(20);

    // then
    assertThat(
            templateReader.readComponentTemplate().template().settings().index().numberOfReplicas())
        .as("should have the default number of shards in template")
        .isEqualTo(0);

    // when
    final PutComponentTemplateRequest request =
        templateReader.getComponentTemplatePutRequest("test-template");

    // then
    assertThat(request.template().settings().index().numberOfReplicas())
        .as("request should have the configured number of replicas")
        .isEqualTo(20);
  }

  @Test
  void shouldReadIndexTemplate() {
    // given
    final var valueType = ValueType.VARIABLE;

    // when
    final Template template = templateReader.readIndexTemplate(valueType);

    // then
    assertThat(template.version()).isEqualTo(1);
    assertThat(template.priority()).isEqualTo(20);

    assertThat(template.composedOf())
        .as("index template contains default composed of the component template")
        .containsExactly("zeebe-record");

    assertThat(template.indexPatterns())
        .as("index template contains default index patterns")
        .containsExactly("zeebe-record_variable_*");

    assertThat(template.template().aliases())
        .containsExactlyEntriesOf(Map.of("zeebe-record-variable", Alias.builder().build()));

    assertThat(template.template().settings().index().numberOfShards()).isEqualTo(1);
    assertThat(template.template().settings().index().numberOfReplicas()).isEqualTo(0);
    assertThat(template.template().settings().index().queries().cache().enabled()).isFalse();
  }

  @Test
  void shouldGetPutIndexTemplateRequest() {
    // given
    final var valueType = ValueType.VARIABLE;

    // when
    final PutIndexTemplateRequest request =
        templateReader.getPutIndexTemplateRequest(
            "test-template", valueType, "searchPattern", "custom-alias");

    // then
    assertThat(request.version()).isEqualTo(1);
    assertThat(request.priority()).isEqualTo(20);

    assertThat(request.composedOf())
        .as("index template contains default composed of the component template")
        .containsExactly("zeebe-record-" + VersionUtil.getVersionLowerCase());

    assertThat(request.indexPatterns())
        .as("index template contains default index patterns")
        .containsExactly("searchPattern");

    assertThat(request.template().aliases())
        .containsExactlyEntriesOf(Map.of("custom-alias", Alias.builder().build()));

    assertThat(request.template().settings().index().numberOfShards()).isEqualTo(1);
    assertThat(request.template().settings().index().numberOfReplicas()).isEqualTo(0);
    assertThat(request.template().settings().index().queries().cache().enabled()).isFalse();

    final Map<String, Property> expectedProperties =
        Map.ofEntries(
            Map.entry("name", Property.builder().keyword(k -> k).build()),
            Map.entry("value", Property.builder().keyword(k -> k.ignoreAbove(8191)).build()),
            Map.entry(
                "scopeKey", Property.builder().long_(LongNumberProperty.builder().build()).build()),
            Map.entry(
                "processInstanceKey",
                Property.builder().long_(LongNumberProperty.builder().build()).build()),
            Map.entry(
                "processDefinitionKey",
                Property.builder().long_(LongNumberProperty.builder().build()).build()),
            Map.entry("bpmnProcessId", Property.builder().keyword(k -> k).build()),
            Map.entry("tenantId", Property.builder().keyword(k -> k).build()),
            Map.entry(
                "rootProcessInstanceKey",
                Property.builder().long_(LongNumberProperty.builder().build()).build()),
            Map.entry(
                "elementInstanceKey",
                Property.builder().long_(LongNumberProperty.builder().build()).build()));

    assertThat(request.template().mappings())
        .as("index template request should have mappings with properties")
        .extracting(TypeMapping::properties)
        .extracting(p -> p.get("value"))
        .asInstanceOf(InstanceOfAssertFactories.type(Property.class))
        .extracting(Property::object)
        .hasFieldOrPropertyWithValue("dynamic", DynamicMapping.Strict)
        .hasFieldOrProperty("properties")
        .extracting(ObjectProperty::properties)
        .asInstanceOf(InstanceOfAssertFactories.map(String.class, Property.class))
        .containsExactlyInAnyOrderEntriesOf(expectedProperties);
  }

  @Test
  void shouldSetNumberOfShardsInIndexTemplate() {
    // given
    final var valueType = ValueType.VARIABLE;
    config.setNumberOfShards(43);

    // then
    assertThat(
            templateReader
                .readIndexTemplate(valueType)
                .template()
                .settings()
                .index()
                .numberOfShards())
        .as("should have the default number of shards in template")
        .isEqualTo(1);

    // when
    final PutIndexTemplateRequest request =
        templateReader.getPutIndexTemplateRequest(
            "test-template", valueType, "searchPattern", "alias");

    // then
    assertThat(request.template().settings().index().numberOfShards())
        .as("request should have the configured number of shards")
        .isEqualTo(43);
  }

  @Test
  void shouldSetNumberOfReplicasInIndexTemplate() {
    // given
    final var valueType = ValueType.VARIABLE;
    config.setNumberOfReplicas(10);

    // then
    assertThat(
            templateReader
                .readIndexTemplate(valueType)
                .template()
                .settings()
                .index()
                .numberOfReplicas())
        .as("should have the default number of shards in template")
        .isEqualTo(0);

    // when
    final PutIndexTemplateRequest request =
        templateReader.getPutIndexTemplateRequest(
            "test-template", valueType, "searchPattern", "alias");

    // then
    assertThat(request.template().settings().index().numberOfReplicas())
        .as("request should have the configured number of replicas")
        .isEqualTo(10);
  }

  @Test
  void shouldReadIndexTemplateWithDifferentPrefix() {
    // given
    config.prefix = "foo-bar";
    final var valueType = ValueType.VARIABLE;

    // when
    final PutIndexTemplateRequest request =
        templateReader.getPutIndexTemplateRequest(
            "test-template", valueType, "searchPattern", "alias");

    // then
    assertThat(request.composedOf())
        .allMatch(composedOf -> composedOf.equals("foo-bar-" + VersionUtil.getVersionLowerCase()));
  }
}
