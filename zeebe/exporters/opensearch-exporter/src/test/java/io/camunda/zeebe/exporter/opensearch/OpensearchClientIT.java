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
import io.camunda.zeebe.exporter.opensearch.TestClient.IndexISMPolicyDto;
import io.camunda.zeebe.exporter.opensearch.TestClient.IndexTemplatesDto.IndexTemplateWrapper;
import io.camunda.zeebe.exporter.opensearch.dto.Template;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.util.VersionUtil;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.opensearch.client.opensearch.indices.IndexTemplate;
import org.opensearch.client.opensearch.indices.PutIndexTemplateRequest;
import org.opensearch.client.opensearch.indices.get_index_template.IndexTemplateItem;

public class OpensearchClientIT {

  private static final int PARTITION_ID = 1;

  @RegisterExtension private static final SearchDBExtension SEARCH_DB = SearchDBExtension.create();

  @AfterEach
  void tearDown() {
    final var policyOptional = SEARCH_DB.client().getIndexStateManagementPolicy();
    if (policyOptional.isPresent()) {
      SEARCH_DB.client().deleteIndexStateManagementPolicy();
    }
  }

  @Test
  void shouldThrowExceptionIfFailToFlushBulk() {
    // given - a record with a negative timestamp will not be indexed because its field in ES is a
    // date, which must be a positive number of milliseconds since the UNIX epoch
    final var invalidRecord =
        SEARCH_DB
            .recordFactory()
            .generateRecord(
                ValueType.VARIABLE,
                b ->
                    b.withTimestamp(Long.MIN_VALUE)
                        .withBrokerVersion(VersionUtil.getVersionLowerCase()));
    SEARCH_DB.client().index(invalidRecord, new RecordSequence(PARTITION_ID, 1));
    SEARCH_DB.client().putComponentTemplate();
    SEARCH_DB.client().putIndexTemplate(ValueType.VARIABLE);

    // when/then
    assertThatThrownBy(SEARCH_DB.client()::flush)
        .isInstanceOf(OpensearchExporterException.class)
        .hasMessageContaining(
            "Failed to flush bulk request: [Failed to flush 1 item(s) of bulk request [type: mapper_parsing_exception, reason: failed to parse field [timestamp]");
  }

  @Test
  void shouldPutIndexTemplate() {
    // given
    final var valueType = ValueType.VARIABLE;

    // required since all index templates are composed with it
    SEARCH_DB.client().putComponentTemplate();

    // when
    SEARCH_DB.client().putIndexTemplate(valueType);

    // then
    final Optional<IndexTemplateItem> maybeIndexTemplateItem =
        SEARCH_DB.testClient().getIndexTemplate(valueType, VersionUtil.getVersionLowerCase());

    assertThat(maybeIndexTemplateItem)
        .as("should have created index template for value type %s", valueType)
        .isPresent();

    final IndexTemplateItem indexTemplateItem = maybeIndexTemplateItem.get();

    final String expectedIndexTemplateName =
        SEARCH_DB
            .indexRouter()
            .indexPrefixForValueType(valueType, VersionUtil.getVersionLowerCase());
    assertThat(indexTemplateItem.name()).isEqualTo(expectedIndexTemplateName);

    final String expectedIndexTemplateAlias =
        SEARCH_DB.indexRouter().aliasNameForValueType(valueType);
    final String expectedSearchPatterns =
        SEARCH_DB
            .indexRouter()
            .searchPatternForValueType(valueType, VersionUtil.getVersionLowerCase());
    final PutIndexTemplateRequest expectedPutIndexTemplateRequest =
        SEARCH_DB
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
    final Template expectedTemplate = SEARCH_DB.templateReader().readComponentTemplate();

    // when
    SEARCH_DB.client().putComponentTemplate();

    // then
    final var templateWrapper = SEARCH_DB.testClient().getComponentTemplate();
    assertThat(templateWrapper)
        .as("should have created component template")
        .isPresent()
        .get()
        .extracting(ComponentTemplateWrapper::name)
        .isEqualTo(SEARCH_DB.config().index.prefix + "-" + VersionUtil.getVersionLowerCase());

    final var template = templateWrapper.get().template();
    assertTemplate(template, expectedTemplate);
  }

  @Test
  @DisabledIfSystemProperty(
      named = TEST_INTEGRATION_OPENSEARCH_AWS_URL,
      matches = "^(?=\\s*\\S).*$",
      disabledReason = "AWS OS IT runners currently support only STS-based authentication")
  void shouldAuthenticateWithBasicAuth() {
    // given
    SEARCH_DB.testClient().putUser("user", "^AHq>z@)&l;RJU=\"", List.of("admin"));
    SEARCH_DB.config().getAuthentication().setUsername("user");
    SEARCH_DB.config().getAuthentication().setPassword("^AHq>z@)&l;RJU=\"");

    // when
    // force recreating the client
    final var authenticatedClient =
        new OpensearchClient(
            SEARCH_DB.config(),
            SEARCH_DB.bulkRequest(),
            OpensearchConnector.of(SEARCH_DB.config()).createClient(),
            RestClientFactory.of(SEARCH_DB.config(), true),
            SEARCH_DB.indexRouter(),
            SEARCH_DB.templateReader(),
            new OpensearchMetrics(new SimpleMeterRegistry()));
    authenticatedClient.putComponentTemplate();

    // then
    assertThat(SEARCH_DB.testClient().getComponentTemplate()).isPresent();
  }

  @Test
  void shouldCreateIndexStateManagementPolicyWithoutTouchingCamundaExporterIndexes() {
    // given

    // when
    SEARCH_DB.client().createIndexStateManagementPolicy();

    // then
    final var ismPolicyResponse = SEARCH_DB.client().getIndexStateManagementPolicy();
    assertThat(ismPolicyResponse).isPresent();
    final var policy = ismPolicyResponse.get().policy();
    assertThat(policy.ismTemplate()).isNotEmpty();
    for (final var ismTemplate : policy.ismTemplate()) {
      assertThat(ismTemplate.indexPatterns())
          .as("ISM template should only apply to zeebe indices")
          .containsOnly(SEARCH_DB.config().index.prefix + "_*");
    }
  }

  @Test
  void shouldBulkAddISMPolicyToAllZeebeIndicesWithoutTouchingCamundaExporterIndexesWithSamePrefix()
      throws IOException {
    // given
    final var config = SEARCH_DB.config();
    final var indexRouter = SEARCH_DB.indexRouter();
    final var ownedIndexName =
        indexRouter.indexPrefixForValueType(ValueType.VARIABLE, VersionUtil.getVersionLowerCase())
            + "_2024-01-01";

    // camunda exporter indexes have a slightly different format
    final var camundaExporterIndexName = config.index.prefix + "-operate-variable-2024-01-01";

    SEARCH_DB.client().createIndexStateManagementPolicy();

    final var osClient = SEARCH_DB.testClient().getOsClient();
    osClient.indices().create(b -> b.index(ownedIndexName));
    osClient.indices().create(b -> b.index(camundaExporterIndexName));

    // when
    SEARCH_DB.client().bulkAddISMPolicyToAllZeebeIndices();

    // then
    Awaitility.await()
        .untilAsserted(
            () -> {
              assertThat(SEARCH_DB.testClient().explainIndex(ownedIndexName))
                  .isPresent()
                  .get()
                  .extracting(IndexISMPolicyDto::policyId)
                  .isEqualTo("zeebe-record-retention-policy");
              assertThat(SEARCH_DB.testClient().explainIndex(camundaExporterIndexName)).isEmpty();
            });
  }

  @Test
  void shouldBulkRemoveISMPolicyFromAllZeebeIndices() throws IOException {
    // given
    final var config = SEARCH_DB.config();
    final var indexRouter = SEARCH_DB.indexRouter();
    final var ownedIndexName =
        indexRouter.indexPrefixForValueType(ValueType.VARIABLE, VersionUtil.getVersionLowerCase())
            + "_2024-01-01";

    SEARCH_DB.client().createIndexStateManagementPolicy();

    final var osClient = SEARCH_DB.testClient().getOsClient();
    osClient.indices().create(b -> b.index(ownedIndexName));

    SEARCH_DB.client().bulkAddISMPolicyToAllZeebeIndices();

    Awaitility.await()
        .untilAsserted(
            () ->
                assertThat(SEARCH_DB.testClient().explainIndex(ownedIndexName))
                    .isPresent()
                    .get()
                    .extracting(IndexISMPolicyDto::policyId)
                    .isEqualTo("zeebe-record-retention-policy"));

    // when
    SEARCH_DB.client().bulkRemoveISMPolicyFromAllZeebeIndices();

    // then
    Awaitility.await()
        .untilAsserted(
            () -> assertThat(SEARCH_DB.testClient().explainIndex(ownedIndexName)).isEmpty());
  }

  private void assertTemplate(final Template actualTemplate, final Template expectedTemplate) {
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
