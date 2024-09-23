/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.connect.plugin;

import static io.camunda.search.connect.plugin.util.TestDatabaseCustomHeaderSupplierImpl.KEY_CUSTOM_HEADER;
import static io.camunda.search.connect.plugin.util.TestDatabaseCustomHeaderSupplierImpl.VALUE_CUSTOM_HEADER;
import static org.apache.hc.core5.http.ContentType.TEXT_HTML;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.plugin.search.header.DatabaseCustomHeaderSupplier;
import io.camunda.search.connect.plugin.util.TestDatabaseCustomHeaderSupplierImpl;
import io.camunda.zeebe.util.jar.ExternalJarRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

public class PluginRepositoryInterceptorTest {

  @Test
  void shouldProcessApache4Headers() {
    final var customHeaderSupplier = new TestDatabaseCustomHeaderSupplierImpl();
    final var plugins = new ArrayList<DatabaseCustomHeaderSupplier>(List.of(customHeaderSupplier));
    final var apache4Req = new org.apache.http.message.BasicHttpRequest("GET", "localhost");
    final var apache4Context = new org.apache.http.protocol.BasicHttpContext();

    final var interceptor = new PluginRepositoryInterceptor(plugins);
    interceptor.process(apache4Req, apache4Context);

    assertThat(apache4Req.getFirstHeader(KEY_CUSTOM_HEADER).getValue())
        .isEqualTo(VALUE_CUSTOM_HEADER);
  }

  @Test
  void shouldProcessApache5Headers() {
    final var customHeaderSupplier = new TestDatabaseCustomHeaderSupplierImpl();
    final var plugins = new ArrayList<DatabaseCustomHeaderSupplier>(List.of(customHeaderSupplier));
    final var apache5Req =
        new org.apache.hc.core5.http.message.BasicHttpRequest("GET", "localhost");
    final var apache5Entity = new org.apache.hc.core5.http.impl.BasicEntityDetails(10, TEXT_HTML);
    final var apache5Context = new org.apache.hc.core5.http.protocol.BasicHttpContext();

    final var interceptor = new PluginRepositoryInterceptor(plugins);
    interceptor.process(apache5Req, apache5Entity, apache5Context);

    assertThat(apache5Req.getFirstHeader(KEY_CUSTOM_HEADER).getValue())
        .isEqualTo(VALUE_CUSTOM_HEADER);
  }

  @Test
  void shouldCreateInterceptorOfRepository() throws IOException {
    final var plugins = new LinkedHashMap<String, Class<? extends DatabaseCustomHeaderSupplier>>();
    plugins.put("PLG1", TestDatabaseCustomHeaderSupplierImpl.class);
    final var repository =
        new PluginRepository(
            plugins, new ExternalJarRepository(), Files.createTempDirectory("plg"));

    final var apache5Req =
        new org.apache.hc.core5.http.message.BasicHttpRequest("GET", "localhost");
    final var apache5Entity = new org.apache.hc.core5.http.impl.BasicEntityDetails(10, TEXT_HTML);
    final var apache5Context = new org.apache.hc.core5.http.protocol.BasicHttpContext();

    final var interceptor = PluginRepositoryInterceptor.ofRepository(repository);
    interceptor.process(apache5Req, apache5Entity, apache5Context);

    assertThat(apache5Req.getFirstHeader(KEY_CUSTOM_HEADER).getValue())
        .isEqualTo(VALUE_CUSTOM_HEADER);
  }
}
