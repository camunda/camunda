/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.appint;

import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.moreThanOrExactly;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.camunda.exporter.appint.config.Config;
import io.camunda.exporter.appint.subscription.SubscriptionFactory;
import io.camunda.exporter.appint.transport.Authentication.ApiKey;
import io.camunda.zeebe.exporter.test.ExporterTestConfiguration;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.RegisterExtension;

@TestInstance(Lifecycle.PER_CLASS)
final class AppIntegrationsExporterBatchIT {

  @RegisterExtension
  public static WireMockExtension wireMock =
      WireMockExtension.extensionOptions().options(wireMockConfig().dynamicPort()).build();

  // omit authorizations since they are removed from the records during serialization
  private final ProtocolFactory factory = new ProtocolFactory(b -> b.withAuthorizations(Map.of()));
  private final ExporterTestController controller = new ExporterTestController();
  private AppIntegrationsExporter exporter;
  private String url;
  private final ExporterTestContext testContext = new ExporterTestContext();
  private Long logPosition = 0L;

  @BeforeEach
  public void beforeAll() {
    url = "http://localhost:" + wireMock.getPort();
  }

  @AfterEach
  public void afterAll() {
    exporter.close();
  }

  @Test
  void shouldExportRecord() {
    // given
    wireMock.stubFor(post("/").willReturn(ok()));
    final var record = generateUserTaskRecords();
    setupExporter(
        config ->
            config
                .setBatchSize(1)
                .setMaxRetries(2)
                .setRetryDelayMs(100L)
                .setRequestTimeoutMs(500L)
                .setContinueOnError(true));

    // when
    exporter.export(record);
    waitForBatchesToComplete();
    final var expectedJson =
        SubscriptionFactory.createJsonMapper()
            .toJson(
                Map.of(
                    "events",
                    List.of(
                        Map.of(
                            "id",
                            String.valueOf(record.getKey()),
                            "type",
                            record.getValueType().name(),
                            "intent",
                            record.getIntent().name(),
                            "userTaskKey",
                            String.valueOf(record.getValue().getUserTaskKey()),
                            "assignee",
                            record.getValue().getAssignee()))));

    // then
    assertThat(controller.getPosition()).isEqualTo(record.getPosition());

    wireMock.verify(
        exactly(1),
        postRequestedFor(urlEqualTo("/"))
            .withHeader(ApiKey.HEADER_NAME, equalTo("test-key"))
            .withRequestBody(equalToJson(expectedJson, true, true)));
  }

  private Record<UserTaskRecordValue> generateUserTaskRecords() {
    return factory.generateRecord(
        ValueType.USER_TASK, r -> r.withPosition(++logPosition).withIntent(UserTaskIntent.CREATED));
  }

  @Test
  void testSingleRecordBatching() {
    setupExporter(config -> config.setBatchSize(1));

    wireMock.stubFor(post(anyUrl()).willReturn(ok()));

    final var records = export(generateRecords().limit(100));

    waitForBatchesToComplete();

    assertThat(controller.getPosition()).isEqualTo(records.getLast().getPosition());
    wireMock.verify(exactly(100), postRequestedFor(urlEqualTo("/")));
  }

  @Test
  void testMultipleRecordBatching() {
    setupExporter(config -> config.setBatchSize(10));

    wireMock.stubFor(post(anyUrl()).willReturn(ok()));

    final var records = export(generateRecords().limit(100));

    waitForBatchesToComplete();

    assertThat(controller.getPosition()).isEqualTo(records.getLast().getPosition());
    wireMock.verify(moreThanOrExactly(10), postRequestedFor(urlEqualTo("/")));
  }

  @Test
  void testRetries() {
    // given
    setupExporter(
        config ->
            config
                .setBatchSize(1)
                .setBatchIntervalMs(10000L)
                .setMaxRetries(2)
                .setRetryDelayMs(100L)
                .setRequestTimeoutMs(5000L)
                .setContinueOnError(false));

    wireMock.stubFor(
        post(anyUrl())
            .inScenario("retry")
            .whenScenarioStateIs(Scenario.STARTED)
            .willSetStateTo("second attempt")
            .willReturn(ResponseDefinitionBuilder.responseDefinition().withStatus(500)));

    wireMock.stubFor(
        post(anyUrl())
            .inScenario("retry")
            .whenScenarioStateIs("second attempt")
            .willSetStateTo("ok")
            .willReturn(ResponseDefinitionBuilder.responseDefinition().withStatus(500)));

    wireMock.stubFor(post(anyUrl()).inScenario("retry").whenScenarioStateIs("ok").willReturn(ok()));

    // when
    final var records = export(generateRecords().limit(1));
    waitForBatchesToComplete();

    // then
    assertThat(controller.getPosition()).isEqualTo(records.getLast().getPosition());
    wireMock.verify(exactly(3), postRequestedFor(urlEqualTo("/")));
  }

  @Test
  void testAllRetriesFailWithContinueOnError() {
    // given
    setupExporter(
        config ->
            config.setBatchSize(1).setMaxRetries(2).setRetryDelayMs(100L).setContinueOnError(true));

    wireMock.stubFor(
        post(anyUrl())
            .inScenario("retry")
            .whenScenarioStateIs(Scenario.STARTED)
            .willSetStateTo("second attempt")
            .willReturn(ResponseDefinitionBuilder.responseDefinition().withStatus(500)));

    wireMock.stubFor(
        post(anyUrl())
            .inScenario("retry")
            .whenScenarioStateIs("second attempt")
            .willSetStateTo("third attempt")
            .willReturn(ResponseDefinitionBuilder.responseDefinition().withStatus(500)));

    wireMock.stubFor(
        post(anyUrl())
            .inScenario("retry")
            .whenScenarioStateIs("third attempt")
            .willReturn(ResponseDefinitionBuilder.responseDefinition().withStatus(500)));

    // when
    final var records = export(generateRecords().limit(1));
    waitForBatchesToComplete();

    // then
    assertThat(controller.getPosition()).isEqualTo(records.getLast().getPosition());
    wireMock.verify(exactly(3), postRequestedFor(urlEqualTo("/")));
  }

  @Test
  void testTimeoutFails() {
    // given
    setupExporter(
        config ->
            config
                .setBatchSize(1)
                .setMaxRetries(2)
                .setRetryDelayMs(100L)
                .setRequestTimeoutMs(500L)
                .setContinueOnError(true));

    wireMock.stubFor(
        post(anyUrl())
            .inScenario("retry")
            .willReturn(
                ResponseDefinitionBuilder.responseDefinition()
                    .withFixedDelay(550)
                    .withStatus(500)));

    // when
    export(generateRecords().limit(1));
    waitForBatchesToComplete();

    // then
    wireMock.verify(exactly(1), postRequestedFor(urlEqualTo("/")));
  }

  @Test
  void testBatchingWithMultipleInFlight() {
    // given
    setupExporter(
        config ->
            config
                .setMaxBatchesInFlight(4)
                .setBatchSize(5)
                .setMaxRetries(2)
                .setRetryDelayMs(100L)
                .setContinueOnError(false));

    wireMock.stubFor(post(anyUrl()).willReturn(ok()));

    // when
    final var records = export(generateRecords().limit(25));
    waitForBatchesToComplete();

    // then
    assertThat(controller.getPosition()).isEqualTo(records.getLast().getPosition());
    wireMock.verify(exactly(5), postRequestedFor(urlEqualTo("/")));
  }

  private void setupExporter(final Consumer<Config> configurator) {
    final var config = new Config().setUrl(url).setApiKey("test-key");
    configurator.accept(config);
    testContext.setConfiguration(new ExporterTestConfiguration<>("test", config));
    exporter = new AppIntegrationsExporter();
    exporter.configure(testContext);
    exporter.open(controller);
  }

  private List<Record<RecordValue>> export(final Stream<Record<RecordValue>> records) {
    final var list = records.toList();
    for (final Record<?> record : list) {
      exporter.export(record);
    }
    return list;
  }

  private Stream<Record<RecordValue>> generateRecords() {
    return factory.generateRecords(
        builder -> {
          final var record =
              factory.generateRecordWithIntent(ValueType.USER_TASK, UserTaskIntent.CREATED);
          return builder
              .withPosition(++logPosition)
              .withIntent(record.getIntent())
              .withValue(record.getValue())
              .withValueType(record.getValueType());
        });
  }

  private void waitForBatchesToComplete() {
    Awaitility.await().until(() -> exporter.getSubscription().hasNoActiveBatch());
  }
}
