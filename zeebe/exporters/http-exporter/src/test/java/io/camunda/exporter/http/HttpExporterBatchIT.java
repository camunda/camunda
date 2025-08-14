/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.http;

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

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import io.camunda.exporter.http.subscription.SubscriptionConfig;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.List;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.Rule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

@TestInstance(Lifecycle.PER_CLASS)
final class HttpExporterBatchIT {

  @Rule
  public WireMockRule wireMockRule =
      new WireMockRule(wireMockConfig().dynamicPort().dynamicHttpsPort());

  // omit authorizations since they are removed from the records during serialization
  private final ProtocolFactory factory = new ProtocolFactory(b -> b.withAuthorizations(Map.of()));
  private final ExporterTestController controller = new ExporterTestController();
  private HttpExporter exporter;
  private String url;
  private final ExporterTestContext testContext = new ExporterTestContext();
  private Long logPosition = 0L;

  @BeforeEach
  public void beforeAll() {
    wireMockRule.resetAll();
    wireMockRule.start();
    configureFor(wireMockRule.port());
    url = "http://localhost:" + wireMockRule.port();
    stubFor(post(anyUrl()).willReturn(ok()));
  }

  @AfterEach
  public void afterAll() {
    wireMockRule.stop();
    exporter.close();
  }

  @Test
  void testSingleRecordBatching() {
    exporter = new HttpExporter(new SubscriptionConfig(url, 1, 100, List.of(), null));
    exporter.configure(testContext);
    exporter.open(controller);

    final var records =
        factory
            .generateRecords((builder -> builder.withPosition(++logPosition)))
            .limit(100)
            .toList();
    for (final Record<?> record : records) {
      exporter.export(record);
    }

    Awaitility.await().until(() -> exporter.getSubscription().getBatch().getSize() == 0);

    verify(exactly(100), postRequestedFor(urlEqualTo("/")));
  }

  @Test
  void testBatching() {
    exporter = new HttpExporter(new SubscriptionConfig(url, 10, 1000, List.of(), null));
    exporter.configure(testContext);
    exporter.open(controller);

    final var records =
        factory
            .generateRecords((builder -> builder.withPosition(++logPosition)))
            .limit(100)
            .toList();
    for (final Record<?> record : records) {
      exporter.export(record);
    }

    Awaitility.await().until(() -> exporter.getSubscription().getBatch().getSize() == 0);

    verify(moreThanOrExactly(10), postRequestedFor(urlEqualTo("/")));
  }
}
