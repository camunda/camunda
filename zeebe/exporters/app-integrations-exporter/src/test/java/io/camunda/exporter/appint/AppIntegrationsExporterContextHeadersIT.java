/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.appint;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.camunda.exporter.appint.config.Config;
import io.camunda.exporter.appint.transport.ContextHeaders;
import io.camunda.zeebe.exporter.test.ExporterTestConfiguration;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

final class AppIntegrationsExporterContextHeadersIT {

  @RegisterExtension
  public static WireMockExtension wireMock =
      WireMockExtension.extensionOptions().options(wireMockConfig().dynamicPort()).build();

  private final ProtocolFactory factory = new ProtocolFactory(b -> b.withAuthorizations(Map.of()));
  private final ExporterTestController controller = new ExporterTestController();
  private AppIntegrationsExporter exporter;
  private String url;

  @BeforeEach
  public void setUp() {
    url = "http://localhost:" + wireMock.getPort();
  }

  @AfterEach
  public void tearDown() {
    if (exporter != null) {
      exporter.close();
    }
  }

  @Test
  void shouldSendContextHeadersCapturedFromConfigAndEnvironment() {
    // given — org id from the environment and cluster id from the exporter config
    wireMock.stubFor(post("/").willReturn(ok()));
    final UnaryOperator<String> env =
        name -> DeploymentContext.ORGANIZATION_ID_ENV_VAR.equals(name) ? "org-from-env" : null;
    final ExporterTestContext context = new ExporterTestContext();
    final Config config =
        new Config()
            .setUrl(url)
            .setApiKey("test-key")
            .setClusterId("cluster-from-config")
            .setBatchSize(1);
    context.setConfiguration(new ExporterTestConfiguration<>("test", config));

    exporter = new AppIntegrationsExporter(env);
    exporter.configure(context);
    exporter.open(controller);

    // when
    exporter.export(generateUserTaskRecord());
    Awaitility.await().until(() -> exporter.getSubscription().hasNoActiveBatch());

    // then
    wireMock.verify(
        exactly(1),
        postRequestedFor(urlEqualTo("/"))
            .withHeader(ContextHeaders.X_ORG_ID, equalTo("org-from-env"))
            .withHeader(ContextHeaders.X_CLUSTER_ID, equalTo("cluster-from-config")));
  }

  private Record<UserTaskRecordValue> generateUserTaskRecord() {
    return factory.generateRecord(
        ValueType.USER_TASK, r -> r.withPosition(1L).withIntent(UserTaskIntent.CREATED));
  }
}
