/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.schema.util.opensearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.connect.OpensearchConnector;
import io.camunda.config.operate.OpensearchProperties;
import io.camunda.config.operate.OperateProperties;
import io.camunda.operate.schema.util.ObservableConnector;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.springframework.context.annotation.Conditional;

@Conditional(OpensearchCondition.class)
public class TestOpenSearchConnector extends OpensearchConnector implements ObservableConnector {

  private final List<Consumer<OperateTestHttpRequest>> requestListeners = new ArrayList<>();

  public TestOpenSearchConnector(
      final OperateProperties operateProperties, final ObjectMapper objectMapper) {
    super(operateProperties, objectMapper);
  }

  /**
   * Adds a request interceptor that a test case can plug in, so that we can assert requests made to
   * OpenSearch
   */
  @Override
  protected HttpAsyncClientBuilder configureHttpClient(
      final HttpAsyncClientBuilder httpAsyncClientBuilder,
      final OpensearchProperties elsConfig,
      final HttpRequestInterceptor... requestInterceptors) {
    httpAsyncClientBuilder.addRequestInterceptorFirst(
        (request, entityDetails, context) ->
            requestListeners.forEach(
                listener ->
                    listener.accept(
                        new OperateTestHttpRequest() {

                          @Override
                          public String getUri() {
                            return request.getRequestUri();
                          }

                          @Override
                          public String getMethod() {
                            return request.getMethod();
                          }
                        })));
    return super.configureHttpClient(httpAsyncClientBuilder, elsConfig);
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
