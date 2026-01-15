/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.appint.exporter;

import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.moreThanOrExactly;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import dev.failsafe.TimeoutExceededException;
import io.camunda.appint.exporter.config.Config;
import io.camunda.zeebe.exporter.test.ExporterTestConfiguration;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.awaitility.Awaitility;
import org.junit.Rule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

@TestInstance(Lifecycle.PER_CLASS)
final class AppIntegrationsExporterBatchIT {

  @Rule
  public WireMockRule wireMockRule =
      new WireMockRule(wireMockConfig().dynamicPort().dynamicHttpsPort());

  // omit authorizations since they are removed from the records during serialization
  private final ProtocolFactory factory = new ProtocolFactory(b -> b.withAuthorizations(Map.of()));
  private final ExporterTestController controller = new ExporterTestController();
  private AppIntegrationsExporter exporter;
  private String url;
  private final ExporterTestContext testContext = new ExporterTestContext();
  private Long logPosition = 0L;

  @BeforeEach
  public void beforeAll() {
    wireMockRule.resetAll();
    wireMockRule.start();
    configureFor(wireMockRule.port());
    url = "http://localhost:" + wireMockRule.port();
  }

  @AfterEach
  public void afterAll() {
    wireMockRule.stop();
    exporter.close();
  }

  @Test
  void testSingleRecordBatching() {
    setupExporter(config -> config.setBatchSize(1));

    stubFor(post(anyUrl()).willReturn(ok()));

    export(generateRecords().limit(100));

    Awaitility.await().until(() -> exporter.getSubscription().getBatch().getSize() == 0);

    verify(exactly(100), postRequestedFor(urlEqualTo("/")));
  }

  @Test
  void testMultipleRecordBatching() {
    setupExporter(config -> config.setBatchSize(10));

    stubFor(post(anyUrl()).willReturn(ok()));

    export(generateRecords().limit(100));

    Awaitility.await().until(() -> exporter.getSubscription().getBatch().getSize() == 0);

    verify(moreThanOrExactly(10), postRequestedFor(urlEqualTo("/")));
  }

  @Test
  void testRetries() {
    setupExporter(
        config ->
            config
                .setBatchSize(1)
                .setBatchIntervalMs(10000L)
                .setMaxRetries(2)
                .setRetryDelayMs(100L)
                .setRequestTimeoutMs(5000L)
                .setContinueOnError(false));

    stubFor(
        post(anyUrl())
            .inScenario("retry")
            .whenScenarioStateIs(Scenario.STARTED)
            .willSetStateTo("second attempt")
            .willReturn(ResponseDefinitionBuilder.responseDefinition().withStatus(500)));

    stubFor(
        post(anyUrl())
            .inScenario("retry")
            .whenScenarioStateIs("second attempt")
            .willSetStateTo("ok")
            .willReturn(ResponseDefinitionBuilder.responseDefinition().withStatus(500)));

    stubFor(post(anyUrl()).inScenario("retry").whenScenarioStateIs("ok").willReturn(ok()));

    export(generateRecords().limit(1));

    Awaitility.await().until(() -> exporter.getSubscription().getBatch().getSize() == 0);

    verify(exactly(3), postRequestedFor(urlEqualTo("/")));
  }

  @Test
  void testAllRetriesFail() {
    setupExporter(
        config ->
            config
                .setBatchSize(1)
                .setMaxRetries(2)
                .setRetryDelayMs(100L)
                .setContinueOnError(false));

    stubFor(
        post(anyUrl())
            .inScenario("retry")
            .whenScenarioStateIs(Scenario.STARTED)
            .willSetStateTo("second attempt")
            .willReturn(ResponseDefinitionBuilder.responseDefinition().withStatus(500)));

    stubFor(
        post(anyUrl())
            .inScenario("retry")
            .whenScenarioStateIs("second attempt")
            .willSetStateTo("third attempt")
            .willReturn(ResponseDefinitionBuilder.responseDefinition().withStatus(500)));

    stubFor(
        post(anyUrl())
            .inScenario("retry")
            .whenScenarioStateIs("third attempt")
            .willReturn(ResponseDefinitionBuilder.responseDefinition().withStatus(500)));

    assertThatThrownBy(() -> export(generateRecords().limit(1)))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Failed to post records, status: 500");

    verify(exactly(3), postRequestedFor(urlEqualTo("/")));
  }

  @Test
  void testTimeoutFails() {
    setupExporter(
        config ->
            config
                .setBatchSize(1)
                .setMaxRetries(2)
                .setRetryDelayMs(100L)
                .setRequestTimeoutMs(500L)
                .setContinueOnError(false));

    stubFor(
        post(anyUrl())
            .inScenario("retry")
            .willReturn(
                ResponseDefinitionBuilder.responseDefinition()
                    .withFixedDelay(550)
                    .withStatus(500)));

    assertThatThrownBy(() -> export(generateRecords().limit(1)))
        .isInstanceOf(RuntimeException.class)
        .hasCauseInstanceOf(TimeoutExceededException.class);

    verify(exactly(1), postRequestedFor(urlEqualTo("/")));
  }

  @Test
  void testAllRetriesFailWithContinueOnError() {
    setupExporter(
        config ->
            config.setBatchSize(1).setMaxRetries(2).setRetryDelayMs(100L).setContinueOnError(true));

    assertThat(exporter.getSubscription().getBatch().getLastLogPosition()).isEqualTo(-1L);

    stubFor(
        post(anyUrl())
            .inScenario("retry")
            .whenScenarioStateIs(Scenario.STARTED)
            .willSetStateTo("second attempt")
            .willReturn(ResponseDefinitionBuilder.responseDefinition().withStatus(500)));

    stubFor(
        post(anyUrl())
            .inScenario("retry")
            .whenScenarioStateIs("second attempt")
            .willSetStateTo("third attempt")
            .willReturn(ResponseDefinitionBuilder.responseDefinition().withStatus(500)));

    stubFor(
        post(anyUrl())
            .inScenario("retry")
            .whenScenarioStateIs("third attempt")
            .willReturn(ResponseDefinitionBuilder.responseDefinition().withStatus(500)));

    final var record = export(generateRecords().limit(1)).getFirst();

    assertThat(exporter.getSubscription().getBatch().getSize()).isEqualTo(0);
    assertThat(exporter.getSubscription().getBatch().getLastLogPosition())
        .isEqualTo(record.getPosition());

    verify(exactly(3), postRequestedFor(urlEqualTo("/")));
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
}
