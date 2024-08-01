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
import io.camunda.operate.connect.OpensearchClientProvider;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.util.ObservableConnector;
import io.camunda.operate.schema.util.ObservableConnector.OperateTestHttpRequest;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.os.OpensearchClientConnector;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.springframework.context.annotation.Conditional;

@Conditional(OpensearchCondition.class)
public class TestOpenSearchConnector extends OpensearchClientProvider
    implements ObservableConnector {

  private List<Consumer<OperateTestHttpRequest>> requestListeners = new ArrayList<>();

  public TestOpenSearchConnector(
      final OperateProperties operateProperties, final ObjectMapper objectMapper) {
    super(operateProperties, objectMapper);
  }

  @Override
  protected OpensearchClientConnector createOpensearchConnector(
      final ConnectConfiguration configuration, final ObjectMapper objectMapper) {
    return new TestHttpClientInterceptorOSConnector(configuration, objectMapper, requestListeners);
  }

  public void addRequestListener(Consumer<OperateTestHttpRequest> listener) {
    this.requestListeners.add(listener);
  }

  public void clearRequestListeners() {
    this.requestListeners.clear();
  }

  private static final class TestHttpClientInterceptorOSConnector
      extends OpensearchClientConnector {

    private final List<Consumer<OperateTestHttpRequest>> requestListeners;

    public TestHttpClientInterceptorOSConnector(
        final ConnectConfiguration configuration,
        final ObjectMapper objectMapper,
        final List<Consumer<OperateTestHttpRequest>> requestListeners) {
      super(configuration, objectMapper);
      this.requestListeners = requestListeners;
    }

    /**
     * Adds a request interceptor that a test case can plug in, so that we can assert requests made
     * to OpenSearch
     */
    @Override
    protected HttpAsyncClientBuilder configureHttpClient(
        final HttpAsyncClientBuilder httpAsyncClientBuilder) {
      httpAsyncClientBuilder.addRequestInterceptorFirst(
          new HttpRequestInterceptor() {

            @Override
            public void process(
                HttpRequest request, EntityDetails entityDetails, HttpContext context)
                throws IOException {
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
                          }));
            }
          });
      return super.configureHttpClient(httpAsyncClientBuilder);
    }
  }
}
