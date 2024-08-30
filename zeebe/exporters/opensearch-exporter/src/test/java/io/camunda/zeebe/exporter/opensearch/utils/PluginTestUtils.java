/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.opensearch.utils;

import io.camunda.zeebe.exporter.opensearch.OpensearchExporterConfiguration;
import io.camunda.zeebe.exporter.opensearch.OpensearchExporterConfiguration.InterceptorPlugin;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.UUID;
import net.bytebuddy.ByteBuddy;
import org.apache.http.nio.client.HttpAsyncClient;
import org.opensearch.client.RestClient;

public class PluginTestUtils {
  public static OpensearchExporterConfiguration.InterceptorPlugin createPluginFromJar(
      final File jarFile, final Class clazz) {
    final var plugin = new InterceptorPlugin();
    plugin.setId(UUID.randomUUID().toString());
    plugin.setClassName(clazz.getName());
    plugin.setJarPath(jarFile.getPath());
    return plugin;
  }

  public static File createCustomHeaderInterceptorJar(final Class clazz) {
    final var byteBuddy = new ByteBuddy();
    final File jar;
    try {
      final var baseDir = Files.createTempDirectory("jarTemp").toFile();
      jar = byteBuddy.decorate(clazz).make().toJar(new File(baseDir, "interceptor.jar"));
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
    return jar;
  }

  public static HttpAsyncClient getHttpClient(final RestClient client) throws Exception {
    final var field = client.getClass().getDeclaredField("client");
    field.setAccessible(true);
    return (HttpAsyncClient) field.get(client);
  }
}
