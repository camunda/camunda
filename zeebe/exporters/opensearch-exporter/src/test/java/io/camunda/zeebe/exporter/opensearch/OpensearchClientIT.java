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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.exporter.opensearch.TestClient.ComponentTemplatesDto.ComponentTemplateWrapper;
import io.camunda.zeebe.exporter.opensearch.TestClient.IndexTemplatesDto.IndexTemplateWrapper;
import io.camunda.zeebe.exporter.opensearch.dto.Template;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.util.VersionUtil;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.generic.Requests;

public class OpensearchClientIT {

  private static final int PARTITION_ID = 1;

  @RegisterExtension private static final SearchDBExtension SEARCH_DB = SearchDBExtension.create();

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
    final String indexTemplateName =
        SEARCH_DB
            .indexRouter()
            .indexPrefixForValueType(valueType, VersionUtil.getVersionLowerCase());
    final String indexTemplateAlias = SEARCH_DB.indexRouter().aliasNameForValueType(valueType);
    final Template expectedTemplate =
        SEARCH_DB
            .templateReader()
            .readIndexTemplate(
                valueType,
                SEARCH_DB
                    .indexRouter()
                    .searchPatternForValueType(valueType, VersionUtil.getVersionLowerCase()),
                indexTemplateAlias);

    // required since all index templates are composed with it
    SEARCH_DB.client().putComponentTemplate();

    // when
    SEARCH_DB.client().putIndexTemplate(valueType);

    // then
    final var templateWrapper =
        SEARCH_DB.testClient().getIndexTemplate(valueType, VersionUtil.getVersionLowerCase());
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
    assertIndexTemplate(template, expectedTemplate);
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
    createISMPolicyIfNeeded();

    // then
    final var ismPolicyResponse = SEARCH_DB.client().getIndexStateManagementPolicy();
    assertThat(ismPolicyResponse).isPresent();
    final var policy = ismPolicyResponse.get().policy();
    assertThat(policy.ismTemplate()).isNotEmpty();
    for (final var ismTemplate : policy.ismTemplate()) {
      assertThat(ismTemplate.indexPatterns())
          .as("ISM template should only apply to zeebe indices")
          .allMatch(pattern -> pattern.startsWith(SEARCH_DB.config().index.prefix + "_*"));
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

    createISMPolicyIfNeeded();

    final var osClient = SEARCH_DB.testClient().getOsClient();
    osClient.indices().create(b -> b.index(ownedIndexName));
    osClient.indices().create(b -> b.index(camundaExporterIndexName));

    // when
    SEARCH_DB.client().bulkAddISMPolicyToAllZeebeIndices();

    // then
    Awaitility.await()
        .untilAsserted(
            () -> {
              assertThat(getISMPolicyIdForIndex(osClient, ownedIndexName))
                  .isEqualTo("zeebe-record-retention-policy");
              assertThat(getISMPolicyIdForIndex(osClient, camundaExporterIndexName)).isNull();
            });
  }

  @Test
  void shouldBulkRemoveISMPolicyToAllZeebeIndices() throws IOException {
    // given
    final var config = SEARCH_DB.config();
    final var indexRouter = SEARCH_DB.indexRouter();
    final var ownedIndexName =
        indexRouter.indexPrefixForValueType(ValueType.VARIABLE, VersionUtil.getVersionLowerCase())
            + "_2024-01-01";

    createISMPolicyIfNeeded();

    final var osClient = SEARCH_DB.testClient().getOsClient();
    osClient.indices().create(b -> b.index(ownedIndexName));

    SEARCH_DB.client().bulkAddISMPolicyToAllZeebeIndices();

    Awaitility.await()
        .untilAsserted(
            () ->
                assertThat(getISMPolicyIdForIndex(osClient, ownedIndexName))
                    .isEqualTo("zeebe-record-retention-policy"));

    // when
    SEARCH_DB.client().bulkRemoveISMPolicyToAllZeebeIndices();

    // then
    Awaitility.await()
        .untilAsserted(() -> assertThat(getISMPolicyIdForIndex(osClient, ownedIndexName)).isNull());
  }

  private static void createISMPolicyIfNeeded() {
    final var policyOptional = SEARCH_DB.client().getIndexStateManagementPolicy();
    if (policyOptional.isEmpty()) {
      SEARCH_DB.client().createIndexStateManagementPolicy();
    }
  }

  private String getISMPolicyIdForIndex(final OpenSearchClient osClient, final String indexName)
      throws IOException {
    final var response =
        osClient
            .generic()
            .execute(
                Requests.builder()
                    .endpoint("/_plugins/_ism/explain/" + indexName)
                    .method("GET")
                    .build());

    final Map<String, Object> json =
        new ObjectMapper().readValue(response.getBody().get().bodyAsString(), Map.class);
    assertThat(json).containsKey(indexName);
    final Map<String, Object> policyDetails = (Map) json.get(indexName);
    assertThat(policyDetails).containsKey("index.plugins.index_state_management.policy_id");
    return (String) policyDetails.get("index.plugins.index_state_management.policy_id");
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
