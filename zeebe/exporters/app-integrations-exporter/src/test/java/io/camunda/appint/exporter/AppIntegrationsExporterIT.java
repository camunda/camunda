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
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
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
import io.camunda.appint.exporter.config.Config;
import io.camunda.appint.exporter.transport.Authentication.ApiKey;
import io.camunda.zeebe.exporter.test.ExporterTestConfiguration;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.Map;
import org.junit.Rule;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

@TestInstance(Lifecycle.PER_CLASS)
final class AppIntegrationsExporterIT {

  @Rule
  public WireMockRule wireMockRule =
      new WireMockRule(wireMockConfig().dynamicPort().dynamicHttpsPort());

  // omit authorizations since they are removed from the records during serialization
  private final ProtocolFactory factory = new ProtocolFactory(b -> b.withAuthorizations(Map.of()));
  private final ExporterTestController controller = new ExporterTestController();
  private AppIntegrationsExporter exporter;
  private final ExporterTestContext testContext = new ExporterTestContext();
  private Long logPosition = 0L;

  @BeforeAll
  public void beforeAll() {
    wireMockRule.start();
    configureFor(wireMockRule.port());
    stubFor(post(anyUrl()).willReturn(ok()));
    final var url = "http://localhost:" + wireMockRule.port();

    final var config = new Config().setUrl(url).setApiKey("test-key").setBatchSize(1);
    testContext.setConfiguration(new ExporterTestConfiguration<>("test", config));

    exporter = new AppIntegrationsExporter();
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

  @Test
  void shouldExportRecord() {
    // given
    final var record = generateUserTaskRecords();

    // when
    exporter.export(record);

    final var expectedJson =
        "[{\"id\":\""
            + record.getKey()
            + "\", \"type\":\""
            + record.getValueType().name()
            + "\", \"intent\":\""
            + record.getIntent().name()
            + "\", \"userTaskKey\":\""
            + record.getValue().getUserTaskKey()
            + "\", \"assignee\": \""
            + record.getValue().getAssignee()
            + "\"}]";

    // then
    verify(
        exactly(1),
        postRequestedFor(urlEqualTo("/"))
            .withHeader(ApiKey.HEADER_NAME, equalTo("test-key"))
            .withRequestBody(equalToJson(expectedJson, true, true)));
  }

  private Record<UserTaskRecordValue> generateUserTaskRecords() {
    return factory.generateRecord(
        ValueType.USER_TASK, r -> r.withPosition(++logPosition).withIntent(UserTaskIntent.CREATED));
  }
}
