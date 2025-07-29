/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.ilm.GetLifecycleRequest;
import io.camunda.zeebe.exporter.TestClient.ComponentTemplatesDto.ComponentTemplateWrapper;
import io.camunda.zeebe.exporter.TestClient.IndexTemplatesDto.IndexTemplateWrapper;
import io.camunda.zeebe.exporter.dto.Template;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import io.camunda.zeebe.util.VersionUtil;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.agrona.CloseHelper;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
final class ElasticsearchClientIT {
  // configuring a superuser will allow us to create more users, which will let us test
  // authentication
  @Container
  private static final ElasticsearchContainer CONTAINER =
      TestSearchContainers.createDefeaultElasticsearchContainer()
          .withEnv("xpack.license.self_generated.type", "trial")
          .withEnv("xpack.security.enabled", "true")
          .withEnv("xpack.security.authc.anonymous.username", "anon")
          .withEnv("xpack.security.authc.anonymous.roles", "superuser")
          .withEnv("xpack.security.authc.anonymous.authz_exception", "true");

  private static final int PARTITION_ID = 1;

  private final ProtocolFactory recordFactory = new ProtocolFactory();
  private final ElasticsearchExporterConfiguration config =
      new ElasticsearchExporterConfiguration();
  private final TemplateReader templateReader = new TemplateReader(config);
  private final RecordIndexRouter indexRouter = new RecordIndexRouter(config.index);
  private final BulkIndexRequest bulkRequest = new BulkIndexRequest();
  private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

  private TestClient testClient;
  private ElasticsearchClient client;

  @BeforeEach
  public void beforeEach() {
    // as all tests use the same endpoint, we need a per-test unique prefix
    config.index.prefix = UUID.randomUUID() + "-test-record";
    config.url = CONTAINER.getHttpHostAddress();

    testClient = new TestClient(config, indexRouter);
    client =
        new ElasticsearchClient(
            config,
            bulkRequest,
            RestClientFactory.of(config),
            indexRouter,
            templateReader,
            new ElasticsearchMetrics(new SimpleMeterRegistry()));
  }

  @AfterEach
  void afterEach() {
    CloseHelper.quietCloseAll(testClient, client);
  }

  @Test
  void shouldThrowExceptionIfFailToFlushBulk() {
    // given - a record with a negative timestamp will not be indexed because its field in ES is a
    // date, which must be a positive number of milliseconds since the UNIX epoch
    final var invalidRecord =
        recordFactory.generateRecord(
            ValueType.VARIABLE,
            b ->
                b.withTimestamp(Long.MIN_VALUE)
                    .withBrokerVersion(VersionUtil.getVersionLowerCase()));
    client.index(invalidRecord, new RecordSequence(PARTITION_ID, 1));
    client.putComponentTemplate();
    client.putIndexTemplate(ValueType.VARIABLE);

    // when/then
    assertThatThrownBy(client::flush)
        .isInstanceOf(ElasticsearchExporterException.class)
        .hasMessageContaining(
            "Failed to flush bulk request: [Failed to flush 1 item(s) of bulk request [type: document_parsing_exception, reason: [1:114] failed to parse field [timestamp] of type [date]");
  }

  @Test
  void shouldPutIndexTemplate() {
    // given
    final var valueType = ValueType.VARIABLE;
    final String indexTemplateName =
        indexRouter.indexPrefixForValueType(valueType, VersionUtil.getVersionLowerCase());
    final String indexTemplateAlias = indexRouter.aliasNameForValueType(valueType);
    final Template expectedTemplate =
        templateReader.readIndexTemplate(
            valueType,
            indexRouter.searchPatternForValueType(valueType, VersionUtil.getVersionLowerCase()),
            indexTemplateAlias);

    // required since all index templates are composed with it
    client.putComponentTemplate();

    // when
    client.putIndexTemplate(valueType);

    // then
    final var templateWrapper =
        testClient.getIndexTemplate(valueType, VersionUtil.getVersionLowerCase());
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
    final Template expectedTemplate = templateReader.readComponentTemplate();

    // when
    client.putComponentTemplate();

    // then
    final var templateWrapper = testClient.getComponentTemplate();
    assertThat(templateWrapper)
        .as("should have created component template")
        .isPresent()
        .get()
        .extracting(ComponentTemplateWrapper::name)
        .isEqualTo(config.index.prefix + "-" + VersionUtil.getVersionLowerCase());

    final var template = templateWrapper.get().template();
    assertIndexTemplate(template, expectedTemplate);
  }

  @Test
  void shouldAuthenticateWithBasicAuth() throws IOException {
    // given
    testClient
        .getEsClient()
        .security()
        .putUser(
            b -> b.username("user").password("password").refresh(Refresh.True).roles("superuser"));
    config.getAuthentication().setUsername("user");
    config.getAuthentication().setPassword("password");

    // when
    // force recreating the client
    final var authenticatedClient = new ElasticsearchClient(config, meterRegistry);
    authenticatedClient.putComponentTemplate();

    // then
    assertThat(testClient.getComponentTemplate()).isPresent();
  }

  @Test
  void shouldPutIndexLifecycleManagementPolicy() throws IOException {
    // given
    config.retention.setEnabled(true);

    // when
    client.putIndexLifecycleManagementPolicy();

    // then
    final var lifecycle =
        testClient
            .getEsClient()
            .ilm()
            .getLifecycle(GetLifecycleRequest.of(b -> b.name(config.retention.getPolicyName())))
            .get(config.retention.getPolicyName());
    assertThat(lifecycle.policy().phases().delete()).isNotNull();
    assertThat(lifecycle.policy().phases().delete().minAge()).isNotNull();
    assertThat(lifecycle.policy().phases().delete().minAge().time()).isEqualTo("30d");
    assertThat(lifecycle.policy().phases().delete().actions()).isNotNull();
    assertThat(lifecycle.policy().phases().delete().actions().delete()).isNotNull();
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
