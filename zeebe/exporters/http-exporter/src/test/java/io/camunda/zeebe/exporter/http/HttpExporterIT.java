/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.http;

import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import io.camunda.zeebe.exporter.http.config.HttpExporterConfiguration;
import io.camunda.zeebe.exporter.test.ExporterTestConfiguration;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableIncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.Map;
import org.junit.Rule;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@TestInstance(Lifecycle.PER_CLASS)
final class HttpExporterIT {

  @Rule
  public WireMockRule wireMockRule =
      new WireMockRule(wireMockConfig().dynamicPort().dynamicHttpsPort());

  // omit authorizations since they are removed from the records during serialization
  private final ProtocolFactory factory = new ProtocolFactory(b -> b.withAuthorizations(Map.of()));
  private final ExporterTestController controller = new ExporterTestController();
  private HttpExporter exporter;
  private final ExporterTestContext testContext = new ExporterTestContext();
  private Long logPosition = 0L;
  private String url;

  @BeforeAll
  public void beforeAll() {
    wireMockRule.start();
    configureFor(wireMockRule.port());
    stubFor(post(anyUrl()).willReturn(ok()));
    url = "http://localhost:" + wireMockRule.port();

    final var config = new HttpExporterConfiguration();
    config.setUrl(url); // Set the URL to the WireMock server
    config.setBatchSize(1);
    config.setConfigPath("classpath:subscription-config.json");
    testContext.setConfiguration(new ExporterTestConfiguration<>("test", config));

    exporter = new HttpExporter();
    exporter.configure(testContext);
    exporter.open(controller);
  }

  @AfterAll
  public void afterAll() {
    exporter.close();
    wireMockRule.stop();
  }

  @BeforeEach
  public void beforeEach() {
    wireMockRule.resetRequests();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("io.camunda.zeebe.exporter.http.TestSupport#provideValueTypes")
  void shouldExportRecord(final ValueType valueType) {
    // given
    final var record = generateRecord(valueType);

    // when
    exporter.export(record);

    // then
    verify(
        exactly(1),
        postRequestedFor(urlEqualTo("/"))
            .withRequestBody(
                equalToJson(
                    "[{\"valueType\":\"" + valueType.name() + "\", \"value\": {}}]", true, true)));
  }

  private <T extends RecordValue> Record<T> generateRecord(final ValueType valueType) {
    return factory.generateRecord(valueType, r -> r.withPosition(++logPosition));
  }

  @Test
  void testWithIncidentRecordAndComplexFiltering() {
    final var config = new HttpExporterConfiguration();
    config.setUrl(url);
    config.setConfigPath("classpath:subscription-config-incident.json");
    testContext.setConfiguration(new ExporterTestConfiguration<>("test", config));
    exporter.configure(testContext);

    // given
    final var record = createIncidentRecord(++logPosition, "testProcess");

    // when
    exporter.export(record);

    // then
    verify(
        exactly(1),
        postRequestedFor(urlEqualTo("/"))
            .withRequestBody(
                equalToJson(
                    "[{\"valueType\":\"INCIDENT\", \"value\": {\"bpmnProcessId\": \"testProcess\"}}]",
                    true,
                    true)));
  }

  private Record<?> createIncidentRecord(final long position, final String bpmnProcessId) {
    final Record<RecordValue> recordValueRecord = factory.generateRecord(ValueType.INCIDENT);
    return factory.generateRecord(
        ValueType.INCIDENT,
        r ->
            r.withIntent(IncidentIntent.CREATED)
                .withPosition(position)
                .withValue(
                    ImmutableIncidentRecordValue.builder()
                        .from((IncidentRecordValue) recordValueRecord.getValue())
                        .withBpmnProcessId(bpmnProcessId)
                        .build()));
  }
}
