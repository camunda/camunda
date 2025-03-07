/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.opensearch;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.camunda.db.se.config.PluginConfiguration;
import io.camunda.plugin.search.header.CustomHeader;
import io.camunda.plugin.search.header.DatabaseCustomHeaderSupplier;
import io.camunda.zeebe.exporter.test.ExporterTestConfiguration;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;

@WireMockTest
final class OpensearchExporterPluginTest {
  private final ProtocolFactory recordFactory = new ProtocolFactory();
  private final OpensearchExporterConfiguration config = new OpensearchExporterConfiguration();
  private final ExporterTestContext context =
      new ExporterTestContext().setConfiguration(new ExporterTestConfiguration<>("test", config));
  private final ExporterTestController controller = new ExporterTestController();

  @AutoClose private final OpensearchExporter exporter = new OpensearchExporter();

  @Test
  void shouldLoadPlugins(final WireMockRuntimeInfo wmRuntimeInfo) {
    // given
    final var pluginConfig = new PluginConfiguration("test", TestPlugin.class.getName(), null);
    final var record = recordFactory.generateRecord();
    config.interceptorPlugins.add(pluginConfig);
    config.url = "http://localhost:" + wmRuntimeInfo.getHttpPort();
    exporter.configure(context);
    exporter.open(controller);
    WireMock.stubFor(WireMock.any(WireMock.anyUrl()).willReturn(WireMock.ok()));

    // when
    try {
      exporter.export(record);
    } catch (final Exception ignored) {
      // ignore export exception since we can't really export anywhere
    }

    // then
    WireMock.verify(
        WireMock.anyRequestedFor(WireMock.anyUrl()).withHeader("foo", WireMock.equalTo("bar")));
  }

  public static final class TestPlugin implements DatabaseCustomHeaderSupplier {

    @Override
    public CustomHeader getSearchDatabaseCustomHeader() {
      return new CustomHeader("foo", "bar");
    }
  }
}
