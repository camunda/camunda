/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.service;

import io.camunda.zeebe.eventplane.store.InvalidStoreConfigException;
import io.camunda.zeebe.eventplane.store.StoreConfig;
import io.camunda.zeebe.eventplane.store.StoreConfig.Type;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * The settings of {@link ServiceApp}: the store, and the service. Each is a system property or, if
 * that isn't set, an environment variable, whose name is the property's with the dots as
 * underscores, the words split by underscores, in upper case: {@code eventplane.store.basePath} is
 * {@code EVENTPLANE_STORE_BASE_PATH}. A blank value counts as unset.
 *
 * <table>
 *   <caption>The settings</caption>
 *   <tr><th>Property</th><th>What</th><th>Default</th></tr>
 *   <tr><td>{@code eventplane.store.type}</td><td>{@code filesystem} or {@code s3}</td>
 *       <td>{@code filesystem}</td></tr>
 *   <tr><td>{@code eventplane.store.path}</td><td>the directory of a filesystem store</td>
 *       <td>required for {@code filesystem}</td></tr>
 *   <tr><td>{@code eventplane.store.bucket}</td><td>the bucket of an S3 store</td>
 *       <td>required for {@code s3}</td></tr>
 *   <tr><td>{@code eventplane.store.basePath}</td><td>a path in the bucket</td><td>none</td></tr>
 *   <tr><td>{@code eventplane.store.region}</td><td>the AWS region</td><td>the AWS SDK's</td></tr>
 *   <tr><td>{@code eventplane.store.endpoint}</td><td>the endpoint of an S3-compatible store</td>
 *       <td>the region's</td></tr>
 *   <tr><td>{@code eventplane.store.accessKeyId}, {@code eventplane.store.secretAccessKey}</td>
 *       <td>static credentials, both or neither</td><td>the AWS SDK's</td></tr>
 *   <tr><td>{@code eventplane.store.forcePathStyle}</td><td>{@code true} for MinIO and
 *       LocalStack</td><td>{@code false}</td></tr>
 *   <tr><td>{@code eventplane.service.host}</td><td>the address to listen on</td>
 *       <td>{@code 0.0.0.0}</td></tr>
 *   <tr><td>{@code eventplane.service.port}</td><td>the port to listen on</td>
 *       <td>{@code 8085}</td></tr>
 *   <tr><td>{@code eventplane.service.peers}</td><td>base URLs of the other instances, separated by
 *       commas</td><td>none</td></tr>
 *   <tr><td>{@code eventplane.service.keepAlive}</td><td>the idle time of a stream before a
 *       keep-alive, as an ISO-8601 duration</td><td>{@code PT15S}</td></tr>
 *   <tr><td>{@code eventplane.service.credentials}</td><td>the JSON object of {@code GET
 *       /v1/credentials}</td><td>the store's own settings</td></tr>
 *   <tr><td>{@code eventplane.service.clusters}</td><td>the stores that clusters write to: a JSON
 *       object of cluster id to store settings, named like the {@code eventplane.store.*} ones
 *       ({@code {"c":{"type":"s3","bucket":"b"}}})</td><td>none</td></tr>
 * </table>
 *
 * @param store how to open the store
 * @param service how to run the service
 */
public record ServiceSettings(StoreConfig store, ServiceConfig service) {

  /** The port that the service listens on if none is set. */
  public static final int DEFAULT_PORT = 8085;

  private static final String STORE = "eventplane.store.";
  private static final String SERVICE = "eventplane.service.";
  private static final Pattern WORD_BOUNDARY = Pattern.compile("([a-z0-9])([A-Z])");

  /** The settings of the system properties and the environment variables. */
  public static ServiceSettings fromEnvironment() {
    return from(ServiceSettings::lookupEnvironment);
  }

  /**
   * The settings that a lookup gives, by property name: the value, or null if there is none.
   *
   * @throws InvalidServiceSettingException if a setting is wrong
   * @throws InvalidStoreConfigException if the store's settings lack what its type needs
   */
  public static ServiceSettings from(final Function<String, @Nullable String> lookup) {
    final Function<String, @Nullable String> settings =
        name -> {
          final var value = lookup.apply(name);
          return value == null || value.isBlank() ? null : value.strip();
        };
    final var store = store(settings);
    final var host = settings.apply(SERVICE + "host");
    final var credentials = settings.apply(SERVICE + "credentials");
    final var clusters = settings.apply(SERVICE + "clusters");
    final var keepAlive = settings.apply(SERVICE + "keepAlive");
    final var address =
        new InetSocketAddress(
            host == null ? "0.0.0.0" : host, port(settings.apply(SERVICE + "port")));
    final var service =
        new ServiceConfig(
            address,
            peers(settings.apply(SERVICE + "peers")),
            keepAlive == null ? ServiceConfig.DEFAULT_KEEP_ALIVE : duration(keepAlive),
            credentials == null ? StoreCredentials.json(store) : credentials,
            clusters == null ? Map.of() : ClusterSettings.parse(SERVICE + "clusters", clusters));
    return new ServiceSettings(store, service);
  }

  private static StoreConfig store(final Function<String, @Nullable String> settings) {
    final var type = settings.apply(STORE + "type");
    final var path = settings.apply(STORE + "path");
    final var endpoint = settings.apply(STORE + "endpoint");
    return new StoreConfig(
        type == null ? Type.FILESYSTEM : Type.of(type),
        path == null ? null : Path.of(path),
        settings.apply(STORE + "bucket"),
        settings.apply(STORE + "basePath"),
        settings.apply(STORE + "region"),
        endpoint == null ? null : uri(STORE + "endpoint", endpoint),
        settings.apply(STORE + "accessKeyId"),
        settings.apply(STORE + "secretAccessKey"),
        bool(STORE + "forcePathStyle", settings.apply(STORE + "forcePathStyle")));
  }

  private static int port(@Nullable final String value) {
    if (value == null) {
      return DEFAULT_PORT;
    }
    try {
      final int port = Integer.parseInt(value);
      if (port >= 0 && port <= 65535) {
        return port;
      }
    } catch (final NumberFormatException e) {
      // reported below
    }
    throw new InvalidServiceSettingException(
        SERVICE + "port", "to be a port from 0 to 65535", "'" + value + "'");
  }

  private static List<URI> peers(@Nullable final String value) {
    if (value == null) {
      return List.of();
    }
    return Arrays.stream(value.split(","))
        .map(String::strip)
        .filter(peer -> !peer.isEmpty())
        .map(peer -> uri(SERVICE + "peers", peer))
        .toList();
  }

  private static Duration duration(final String value) {
    try {
      return Duration.parse(value);
    } catch (final DateTimeParseException e) {
      throw new InvalidServiceSettingException(
          SERVICE + "keepAlive", "to be an ISO-8601 duration, such as PT15S", "'" + value + "'");
    }
  }

  static URI uri(final String setting, final String value) {
    try {
      return new URI(value);
    } catch (final URISyntaxException e) {
      throw new InvalidServiceSettingException(setting, "to be a URI", "'" + value + "'");
    }
  }

  private static boolean bool(final String setting, @Nullable final String value) {
    if (value == null || "false".equalsIgnoreCase(value)) {
      return false;
    }
    if ("true".equalsIgnoreCase(value)) {
      return true;
    }
    throw new InvalidServiceSettingException(setting, "to be true or false", "'" + value + "'");
  }

  /** The system property of the name, or the environment variable that it names. */
  private static @Nullable String lookupEnvironment(final String property) {
    final var value = System.getProperty(property);
    return value != null ? value : System.getenv(environmentVariable(property));
  }

  /**
   * The environment variable of a property: {@code eventplane.store.basePath} is {@code
   * EVENTPLANE_STORE_BASE_PATH}.
   */
  static String environmentVariable(final String property) {
    return WORD_BOUNDARY
        .matcher(property)
        .replaceAll("$1_$2")
        .replace('.', '_')
        .toUpperCase(Locale.ROOT);
  }
}
