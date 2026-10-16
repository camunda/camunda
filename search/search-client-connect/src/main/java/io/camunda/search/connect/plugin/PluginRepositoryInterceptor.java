/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.connect.plugin;

import io.camunda.plugin.search.header.DatabaseCustomHeaderSupplier;
import java.util.SequencedCollection;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.protocol.HttpContext;

final class PluginRepositoryInterceptor implements HttpRequestInterceptor {

  // The order of plugins is important.
  private final SequencedCollection<DatabaseCustomHeaderSupplier> databaseHeaderPlugins;

  PluginRepositoryInterceptor(final SequencedCollection<DatabaseCustomHeaderSupplier> plugins) {
    databaseHeaderPlugins = plugins;
  }

  static PluginRepositoryInterceptor ofRepository(final PluginRepository repository) {
    final var interceptors = repository.instantiatePlugins().toList();
    return new PluginRepositoryInterceptor(interceptors);
  }

  @Override
  public void process(
      final HttpRequest request, final EntityDetails entity, final HttpContext context) {
    for (final var plugin : databaseHeaderPlugins) {
      setHeader(plugin, request::setHeader);
    }
  }

  private void setHeader(
      final DatabaseCustomHeaderSupplier plugin, final HeaderConsumer headerConsumer) {
    final var header = plugin.getSearchDatabaseCustomHeader();
    headerConsumer.accept(header.key(), header.value());
  }

  private interface HeaderConsumer {
    void accept(final String key, final String value);
  }
}
