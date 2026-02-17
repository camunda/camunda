/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.schema.util.elasticsearch;

import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.connect.ElasticsearchConnector;
import io.camunda.operate.property.ElasticsearchProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.util.ObservableConnector;
import io.camunda.operate.schema.util.ObservableConnector.OperateTestHttpRequest;
import io.camunda.search.connect.es.builder.ElasticsearchClientBuilder;
import io.camunda.search.connect.plugin.PluginRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.apache.http.HttpRequestInterceptor;
import org.springframework.context.annotation.Conditional;

@Conditional(ElasticsearchCondition.class)
public class TestElasticsearchConnector extends ElasticsearchConnector
    implements ObservableConnector {

  private final List<Consumer<OperateTestHttpRequest>> requestListeners = new ArrayList<>();

  public TestElasticsearchConnector(final OperateProperties operateProperties) {
    super(operateProperties);
  }

  /**
   * Overrides the builder configuration to add a request interceptor that test cases can use to
   * assert requests made to Elasticsearch.
   */
  @Override
  protected ElasticsearchClientBuilder configureBuilder(
      final ElasticsearchProperties elsConfig, final PluginRepository pluginRepository) {
    return super.configureBuilder(elsConfig, pluginRepository)
        .withRequestInterceptors(
            (HttpRequestInterceptor)
                (request, context) -> {
                  final var requestLine = request.getRequestLine();
                  requestListeners.forEach(
                      listener ->
                          listener.accept(
                              new OperateTestHttpRequest() {
                                @Override
                                public String getUri() {
                                  return requestLine.getUri();
                                }

                                @Override
                                public String getMethod() {
                                  return requestLine.getMethod();
                                }
                              }));
                });
  }

  @Override
  public void addRequestListener(final Consumer<OperateTestHttpRequest> listener) {
    requestListeners.add(listener);
  }

  @Override
  public void clearRequestListeners() {
    requestListeners.clear();
  }
}
