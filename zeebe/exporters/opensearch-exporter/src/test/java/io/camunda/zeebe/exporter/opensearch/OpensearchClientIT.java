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

import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.util.VersionUtil;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Optional;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.opensearch.client.opensearch.cluster.ComponentTemplate;
import org.opensearch.client.opensearch.cluster.ComponentTemplateSummary;
import org.opensearch.client.opensearch.cluster.GetComponentTemplateResponse;
import org.opensearch.client.opensearch.cluster.PutComponentTemplateRequest;
import org.opensearch.client.opensearch.indices.IndexTemplate;
import org.opensearch.client.opensearch.indices.PutIndexTemplateRequest;
import org.opensearch.client.opensearch.indices.get_index_template.IndexTemplateItem;

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

    // required since all index templates are composed with it
    searchDB.client().putComponentTemplate();

    // when
    searchDB.client().putIndexTemplate(valueType);

    // then
    final Optional<IndexTemplateItem> maybeIndexTemplateItem =
        searchDB.testClient().maybeGetIndexTemplate(valueType, VersionUtil.getVersionLowerCase());

    assertThat(maybeIndexTemplateItem)
        .as("should have created index template for value type %s", valueType)
        .isPresent();

    final IndexTemplateItem indexTemplateItem = maybeIndexTemplateItem.get();

    final String expectedIndexTemplateName =
        searchDB
            .indexRouter()
            .indexPrefixForValueType(valueType, VersionUtil.getVersionLowerCase());
    assertThat(indexTemplateItem.name()).isEqualTo(expectedIndexTemplateName);

    final String expectedIndexTemplateAlias =
        searchDB.indexRouter().aliasNameForValueType(valueType);
    final String expectedSearchPatterns =
        searchDB
            .indexRouter()
            .searchPatternForValueType(valueType, VersionUtil.getVersionLowerCase());
    final PutIndexTemplateRequest expectedPutIndexTemplateRequest =
        searchDB
            .templateReader()
            .getPutIndexTemplateRequest(
                expectedIndexTemplateName,
                valueType,
                expectedSearchPatterns,
                expectedIndexTemplateAlias);

    assertIndexTemplate(indexTemplateItem.indexTemplate(), expectedPutIndexTemplateRequest);
  }

  @Test
  void shouldPutComponentTemplate() {
    // given
    searchDB.client().putComponentTemplate();

    // then
    final Optional<GetComponentTemplateResponse> maybeGetComponentTemplate =
        searchDB.testClient().maybeGetComponentTemplate();

    assertThat(maybeGetComponentTemplate).as("should have created component template").isPresent();

    final GetComponentTemplateResponse componentTemplateResponse = maybeGetComponentTemplate.get();

    final String expectedComponentTemplateName =
        searchDB.config().index.prefix + "-" + VersionUtil.getVersionLowerCase();

    final ComponentTemplate firstComponentTemplate =
        assertThat(componentTemplateResponse)
            .extracting(GetComponentTemplateResponse::componentTemplates)
            .asInstanceOf(InstanceOfAssertFactories.list(ComponentTemplate.class))
            .hasSize(1)
            .first()
            .actual();

    assertThat(firstComponentTemplate.name()).isEqualTo(expectedComponentTemplateName);

    // and
    final PutComponentTemplateRequest componentTemplatePutRequest =
        searchDB.templateReader().getComponentTemplatePutRequest(expectedComponentTemplateName);

    assertComponentTemplate(firstComponentTemplate, componentTemplatePutRequest);
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
            OpensearchConnector.of(searchDB.config()).createClient(),
            RestClientFactory.of(searchDB.config(), true),
            searchDB.indexRouter(),
            searchDB.templateReader(),
            new OpensearchMetrics(new SimpleMeterRegistry()));
    authenticatedClient.putComponentTemplate();

    // then
    assertThat(searchDB.testClient().maybeGetComponentTemplate()).isPresent();
  }

  private void assertComponentTemplate(
      final ComponentTemplate actualTemplate,
      final PutComponentTemplateRequest putComponentTemplateRequest) {
    final ComponentTemplateSummary actualComponentTemplate =
        actualTemplate.componentTemplate().template();

    assertThat(actualTemplate.componentTemplate().version())
        .isEqualTo(putComponentTemplateRequest.version());
    assertThat(actualComponentTemplate.mappings())
        .isEqualTo(putComponentTemplateRequest.template().mappings());

    assertThat(actualComponentTemplate.settings().get("index"))
        .isEqualTo(putComponentTemplateRequest.template().settings().index());
  }

  private void assertIndexTemplate(
      final IndexTemplate actualTemplate, final PutIndexTemplateRequest putIndexTemplateRequest) {
    assertThat(actualTemplate.indexPatterns()).isEqualTo(putIndexTemplateRequest.indexPatterns());
    assertThat(actualTemplate.composedOf()).isEqualTo(putIndexTemplateRequest.composedOf());
    assertThat(actualTemplate.priority())
        .isEqualTo(Long.valueOf(putIndexTemplateRequest.priority()));
    assertThat(actualTemplate.version()).isEqualTo(putIndexTemplateRequest.version());
    assertThat(actualTemplate.template().aliases())
        .isEqualTo(putIndexTemplateRequest.template().aliases());
    assertThat(actualTemplate.template().mappings())
        .isEqualTo(putIndexTemplateRequest.template().mappings());

    // simply this catches all cases
    assertThat(actualTemplate.template().settings())
        .isEqualTo(putIndexTemplateRequest.template().settings());

    // also explicitly check for number_of_shards, number_of_replicas and queries settings, as those
    // are the ones we set in the template, and we want to make sure they are correctly applied
    assertThat(actualTemplate.template().settings().index().numberOfShards())
        .isEqualTo(putIndexTemplateRequest.template().settings().index().numberOfShards());
    assertThat(actualTemplate.template().settings().index().numberOfReplicas())
        .isEqualTo(putIndexTemplateRequest.template().settings().index().numberOfReplicas());
    assertThat(actualTemplate.template().settings().index().queries())
        .isEqualTo(putIndexTemplateRequest.template().settings().index().queries());
  }
}
