/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.opensearch;

import static io.camunda.zeebe.exporter.opensearch.SearchDBExtension.TEST_INTEGRATION_OPENSEARCH_AWS_URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.exporter.opensearch.TestClient.ComponentTemplatesDto.ComponentTemplateWrapper;
import io.camunda.zeebe.exporter.opensearch.TestClient.IndexTemplatesDto.IndexTemplateWrapper;
import io.camunda.zeebe.exporter.opensearch.dto.Template;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.util.VersionUtil;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;

public class OpensearchClientIT {

  private static final int PARTITION_ID = 1;

  @RegisterExtension private static SearchDBExtension searchDB = SearchDBExtension.create();

  @Test
  void shouldThrowExceptionIfFailToFlushBulk() {
    // given - a record with a negative timestamp will not be indexed because its field in ES is a
    // date, which must be a positive number of milliseconds since the UNIX epoch
    final var invalidRecord =
        searchDB
            .recordFactory()
            .generateRecord(
                ValueType.VARIABLE,
                b ->
                    b.withTimestamp(Long.MIN_VALUE)
                        .withBrokerVersion(VersionUtil.getVersionLowerCase()));
    searchDB.client().index(invalidRecord, new RecordSequence(PARTITION_ID, 1));
    searchDB.client().putComponentTemplate();
    searchDB.client().putIndexTemplate(ValueType.VARIABLE);

    // when/then
    assertThatThrownBy(searchDB.client()::flush)
        .isInstanceOf(OpensearchExporterException.class)
        .hasMessageContaining(
            "Failed to flush bulk request: [Failed to flush 1 item(s) of bulk request [type: mapper_parsing_exception, reason: failed to parse field [timestamp]");
  }

  @Test
  void shouldPutIndexTemplate() {
    // given
    final var valueType = ValueType.VARIABLE;
    final String indexTemplateName =
        searchDB
            .indexRouter()
            .indexPrefixForValueType(valueType, VersionUtil.getVersionLowerCase());
    final String indexTemplateAlias = searchDB.indexRouter().aliasNameForValueType(valueType);
    final Template expectedTemplate =
        searchDB
            .templateReader()
            .readIndexTemplate(
                valueType,
                searchDB
                    .indexRouter()
                    .searchPatternForValueType(valueType, VersionUtil.getVersionLowerCase()),
                indexTemplateAlias);

    // required since all index templates are composed with it
    searchDB.client().putComponentTemplate();

    // when
    searchDB.client().putIndexTemplate(valueType);

    // then
    final var templateWrapper =
        searchDB.testClient().getIndexTemplate(valueType, VersionUtil.getVersionLowerCase());
    assertThat(templateWrapper)
        .as("should have created template for value type %s", valueType)
        .isPresent()
        .get()
        .extracting(IndexTemplateWrapper::name)
        .isEqualTo(indexTemplateName);

    final var template = templateWrapper.get().template();
    assertIndexTemplate(template, expectedTemplate);
  }

  @Test
  void shouldPutComponentTemplate() {
    // given
    final Template expectedTemplate = searchDB.templateReader().readComponentTemplate();

    // when
    searchDB.client().putComponentTemplate();

    // then
    final var templateWrapper = searchDB.testClient().getComponentTemplate();
    assertThat(templateWrapper)
        .as("should have created component template")
        .isPresent()
        .get()
        .extracting(ComponentTemplateWrapper::name)
        .isEqualTo(searchDB.config().index.prefix + "-" + VersionUtil.getVersionLowerCase());

    final var template = templateWrapper.get().template();
    assertIndexTemplate(template, expectedTemplate);
  }

  @Test
  @DisabledIfSystemProperty(
      named = TEST_INTEGRATION_OPENSEARCH_AWS_URL,
      matches = "^(?=\\s*\\S).*$",
      disabledReason = "AWS OS IT runners currently support only STS-based authentication")
  void shouldAuthenticateWithBasicAuth() {
    // given
    searchDB.testClient().putUser("user", "^AHq>z@)&l;RJU=\"", List.of("admin"));
    searchDB.config().getAuthentication().setUsername("user");
    searchDB.config().getAuthentication().setPassword("^AHq>z@)&l;RJU=\"");

    // when
    // force recreating the client
    final var authenticatedClient =
        new OpensearchClient(
            searchDB.config(),
            searchDB.bulkRequest(),
            RestClientFactory.of(searchDB.config(), true),
            searchDB.indexRouter(),
            searchDB.templateReader(),
            new OpensearchMetrics(new SimpleMeterRegistry()));
    authenticatedClient.putComponentTemplate();

    // then
    assertThat(searchDB.testClient().getComponentTemplate()).isPresent();
  }

  private void assertIndexTemplate(final Template actualTemplate, final Template expectedTemplate) {
    assertThat(actualTemplate.patterns()).isEqualTo(expectedTemplate.patterns());
    assertThat(actualTemplate.composedOf()).isEqualTo(expectedTemplate.composedOf());
    assertThat(actualTemplate.priority()).isEqualTo(expectedTemplate.priority());
    assertThat(actualTemplate.version()).isEqualTo(expectedTemplate.version());
    assertThat(actualTemplate.template().aliases())
        .isEqualTo(expectedTemplate.template().aliases());
    assertThat(actualTemplate.template().mappings())
        .isEqualTo(expectedTemplate.template().mappings());

    // cannot compare settings because we never get flat settings, instead we get { index : {
    // number_of_shards: 1, queries: { cache : { enabled : false } } } }
    // so instead we decompose how we compare the settings. I've tried with flat_settings parameter
    // but that doesn't seem to be doing anything
    assertThat(actualTemplate.template().settings())
        .as("should contain a map of index settings")
        .extractingByKey("index")
        .isInstanceOf(Map.class)
        .asInstanceOf(InstanceOfAssertFactories.map(String.class, Object.class))
        .containsEntry("number_of_shards", "1")
        .containsEntry("number_of_replicas", "0")
        .containsEntry("queries", Map.of("cache", Map.of("enabled", "false")));
  }
}
