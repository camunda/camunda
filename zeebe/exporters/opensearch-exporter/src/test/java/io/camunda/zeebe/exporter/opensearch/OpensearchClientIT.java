/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.opensearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.exporter.opensearch.TestClient.ComponentTemplatesDto.ComponentTemplateWrapper;
import io.camunda.zeebe.exporter.opensearch.TestClient.IndexTemplatesDto.IndexTemplateWrapper;
import io.camunda.zeebe.exporter.opensearch.dto.Template;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.agrona.CloseHelper;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opensearch.testcontainers.OpensearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
final class OpensearchClientIT {
  private static final String PASSWORD = "P@a$5w0rd";
  private static final String ADMIN_PASSWORD_ENV_VAR = "OPENSEARCH_INITIAL_ADMIN_PASSWORD";

  @Container
  private static final OpensearchContainer<?> CONTAINER =
      TestSupport.createDefaultContainer()
          .withSecurityEnabled()
          .withEnv(ADMIN_PASSWORD_ENV_VAR, PASSWORD);

  private static final int PARTITION_ID = 1;

  private final ProtocolFactory recordFactory = new ProtocolFactory();
  private final OpensearchExporterConfiguration config = new OpensearchExporterConfiguration();
  private final TemplateReader templateReader = new TemplateReader(config.index);
  private final RecordIndexRouter indexRouter = new RecordIndexRouter(config.index);
  private final BulkIndexRequest bulkRequest = new BulkIndexRequest();

  private TestClient testClient;
  private OpensearchClient client;

  @BeforeEach
  public void beforeEach() {
    // as all tests use the same endpoint, we need a per-test unique prefix
    config.index.prefix = UUID.randomUUID() + "-test-record";
    config.url = CONTAINER.getHttpHostAddress();
    config.getAuthentication().setUsername(CONTAINER.getUsername());
    config.getAuthentication().setPassword(PASSWORD);
    testClient = new TestClient(config, indexRouter);
    client =
        new OpensearchClient(
            config,
            bulkRequest,
            RestClientFactory.of(config, true),
            indexRouter,
            templateReader,
            new OpensearchMetrics(new SimpleMeterRegistry()));
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
        recordFactory.generateRecord(ValueType.VARIABLE, b -> b.withTimestamp(Long.MIN_VALUE));
    client.index(invalidRecord, new RecordSequence(PARTITION_ID, 1));
    client.putComponentTemplate();
    client.putIndexTemplate(ValueType.VARIABLE);

    // when/then
    assertThatThrownBy(client::flush)
        .isInstanceOf(OpensearchExporterException.class)
        .hasMessageContaining(
            "Failed to flush bulk request: [Failed to flush 1 item(s) of bulk request [type: mapper_parsing_exception, reason: failed to parse field [timestamp]");
  }

  @Test
  void shouldPutIndexTemplate() {
    // given
    final var valueType = ValueType.VARIABLE;
    final String indexTemplateName = indexRouter.indexPrefixForValueType(valueType);
    final String indexTemplateAlias = indexRouter.aliasNameForValueType(valueType);
    final Template expectedTemplate =
        templateReader.readIndexTemplate(
            valueType, indexRouter.searchPatternForValueType(valueType), indexTemplateAlias);

    // required since all index templates are composed with it
    client.putComponentTemplate();

    // when
    client.putIndexTemplate(valueType);

    // then
    final var templateWrapper = testClient.getIndexTemplate(valueType);
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
        .isEqualTo(config.index.prefix);

    final var template = templateWrapper.get().template();
    assertIndexTemplate(template, expectedTemplate);
  }

  @Test
  void shouldAuthenticateWithBasicAuth() {
    // given
    testClient.putUser("user", "^AHq>z@)&l;RJU=\"", List.of("admin"));
    config.getAuthentication().setUsername("user");
    config.getAuthentication().setPassword("^AHq>z@)&l;RJU=\"");

    // when
    // force recreating the client
    final var authenticatedClient =
        new OpensearchClient(
            config,
            bulkRequest,
            RestClientFactory.of(config, true),
            indexRouter,
            templateReader,
            new OpensearchMetrics(new SimpleMeterRegistry()));
    authenticatedClient.putComponentTemplate();

    // then
    assertThat(testClient.getComponentTemplate()).isPresent();
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
