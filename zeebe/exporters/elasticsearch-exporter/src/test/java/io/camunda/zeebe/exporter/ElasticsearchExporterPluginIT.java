/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter;

import static io.camunda.zeebe.exporter.utils.PluginTestUtils.createCustomHeaderInterceptorJar;
import static io.camunda.zeebe.exporter.utils.PluginTestUtils.createPluginFromJar;
import static io.camunda.zeebe.exporter.utils.TestStaticCustomHeaderInterceptor.X_CUSTOM_HEADER;
import static io.camunda.zeebe.exporter.utils.TestStaticCustomHeaderInterceptor.X_CUSTOM_HEADER_VALUE;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.exporter.test.ExporterTestConfiguration;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;
import io.camunda.zeebe.exporter.utils.NoopHTTPCallback;
import io.camunda.zeebe.exporter.utils.TestStaticCustomHeaderInterceptor;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.value.ImmutableJobRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.agrona.CloseHelper;
import org.apache.http.HttpHost;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.protocol.BasicHttpContext;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@TestInstance(Lifecycle.PER_CLASS)
final class ElasticsearchExporterPluginIT {

  private static final String HTTP_CONTEXT_REQUEST_ATTRIBUTE_ID = "http.request";

  @Container
  private static final ElasticsearchContainer CONTAINER = TestSupport.createDefaultContainer();

  private final ElasticsearchExporterConfiguration config =
      new ElasticsearchExporterConfiguration();
  private final ProtocolFactory factory = new ProtocolFactory();
  private final ExporterTestController controller = new ExporterTestController();
  private final ElasticsearchExporter exporter = new ElasticsearchExporter();
  private final RecordIndexRouter indexRouter = new RecordIndexRouter(config.index);

  private RestClient restClient = RestClientFactory.of(config);

  private TestClient testClient;
  private ExporterTestContext exporterTestContext;

  @BeforeAll
  public void beforeAll() {
    config.url = CONTAINER.getHttpHostAddress();
    config.index.setNumberOfShards(1);
    config.index.setNumberOfReplicas(1);
    config.index.createTemplate = true;
    config.bulk.size = 1; // force flushing on the first record
    // here; enable all indexes that needed during the tests beforehand as they will be created once
    TestSupport.provideValueTypes()
        .forEach(valueType -> TestSupport.setIndexingForValueType(config.index, valueType, true));

    testClient = new TestClient(config, indexRouter, restClient);

    exporterTestContext =
        new ExporterTestContext()
            .setConfiguration(new ExporterTestConfiguration<>("elastic", config));
    exporter.configure(exporterTestContext);
    exporter.open(controller);
  }

  @AfterEach
  void afterEach() {
    config.interceptorPlugins.clear();
  }

  @AfterAll
  void afterAll() {
    CloseHelper.quietCloseAll(testClient);
  }

  @Test
  void shouldAddStaticCustomHeader() {
    // given
    final JobRecordValue value =
        ImmutableJobRecordValue.builder()
            .from(factory.generateObject(JobRecordValue.class))
            .build();
    final Record<JobRecordValue> record =
        factory.generateRecord(ValueType.JOB, builder -> builder.withValue(value));

    final var pluginClass = TestStaticCustomHeaderInterceptor.class;
    final var plugin =
        createPluginFromJar(createCustomHeaderInterceptorJar(pluginClass), pluginClass);
    config.interceptorPlugins.put(plugin.getId(), plugin);

    restClient = RestClientFactory.of(config);
    testClient = new TestClient(config, indexRouter, restClient);
    final var context = new BasicHttpContext();

    exporter.export(record);

    // when
    restClient
        .getHttpClient()
        .execute(
            HttpHost.create(CONTAINER.getHttpHostAddress()),
            new HttpGet(),
            context,
            NoopHTTPCallback.INSTANCE);

    // then
    assertThat(
            ((HttpRequestWrapper) context.getAttribute(HTTP_CONTEXT_REQUEST_ATTRIBUTE_ID))
                .getFirstHeader(X_CUSTOM_HEADER)
                .getValue())
        .isEqualTo(X_CUSTOM_HEADER_VALUE);
  }
}
