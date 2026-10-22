/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.maintenance;

import io.camunda.zeebe.eventplane.store.ObjectStores;
import io.camunda.zeebe.eventplane.store.S3StoreConfig;
import io.camunda.zeebe.eventplane.store.StoreConfig;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.InstantSource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs {@link Maintenance} on a store at a fixed interval until the process is stopped. It logs
 * through SLF4J, so an SLF4J binding must be on the classpath to see it.
 *
 * <p>Settings are system properties, or else environment variables, which are the property's name
 * in upper case with its dots as underscores: {@code eventplane.store.type} is {@code
 * EVENTPLANE_STORE_TYPE}.
 *
 * <ul>
 *   <li>{@code eventplane.store.type}: {@code filesystem} or {@code s3}; required
 *   <li>{@code eventplane.store.path}: the root directory; required for {@code filesystem}
 *   <li>{@code eventplane.store.bucket}: the bucket; required for {@code s3}
 *   <li>{@code eventplane.store.basePath}: a prefix of every key, for {@code s3}
 *   <li>{@code eventplane.store.region}: the region, for {@code s3}
 *   <li>{@code eventplane.store.endpoint}: the endpoint's URI, for MinIO and the like
 *   <li>{@code eventplane.store.accessKeyId} and {@code eventplane.store.secretAccessKey}: both, or
 *       neither for the default credentials
 *   <li>{@code eventplane.store.forcePathStyle}: {@code true} for MinIO and the like
 *   <li>{@code eventplane.maintenance.interval}: ISO-8601 duration between runs; {@code PT10M}
 *   <li>{@code eventplane.maintenance.rollupAge}: ISO-8601 duration; {@code PT24H}
 *   <li>{@code eventplane.maintenance.rollupTargetBytes}: a number; {@code 268435456}
 *   <li>{@code eventplane.maintenance.retention}: ISO-8601 duration; unset keeps everything
 * </ul>
 */
public final class MaintenanceApp {

  static final String STORE_TYPE = "eventplane.store.type";
  static final String STORE_PATH = "eventplane.store.path";
  static final String STORE_BUCKET = "eventplane.store.bucket";
  static final String STORE_BASE_PATH = "eventplane.store.basePath";
  static final String STORE_REGION = "eventplane.store.region";
  static final String STORE_ENDPOINT = "eventplane.store.endpoint";
  static final String STORE_ACCESS_KEY_ID = "eventplane.store.accessKeyId";
  static final String STORE_SECRET_ACCESS_KEY = "eventplane.store.secretAccessKey";
  static final String STORE_FORCE_PATH_STYLE = "eventplane.store.forcePathStyle";
  static final String INTERVAL = "eventplane.maintenance.interval";
  static final String ROLLUP_AGE = "eventplane.maintenance.rollupAge";
  static final String ROLLUP_TARGET_BYTES = "eventplane.maintenance.rollupTargetBytes";
  static final String RETENTION = "eventplane.maintenance.retention";

  static final Duration DEFAULT_INTERVAL = Duration.ofMinutes(10);

  private static final Logger LOG = LoggerFactory.getLogger(MaintenanceApp.class);

  private MaintenanceApp() {}

  public static void main(final String[] args) throws Exception {
    final var settings = Settings.system();
    final var storeConfig = storeConfig(settings);
    final var config = maintenanceConfig(settings);
    final var interval = interval(settings);

    final var stop = new CountDownLatch(1);
    final var main = Thread.currentThread();
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  stop.countDown();
                  try {
                    main.join();
                  } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                  }
                },
                "maintenance-shutdown"));

    try (final var store = ObjectStores.open(storeConfig)) {
      LOG.info("Maintaining the store {} every {} with {}", store.root(), interval, config);
      runEvery(new Maintenance(store, config, InstantSource.system()), interval, stop);
    }
  }

  /** Runs maintenance, and again after each interval, until {@code stop} is counted down. */
  static void runEvery(
      final Maintenance maintenance, final Duration interval, final CountDownLatch stop)
      throws InterruptedException {
    do {
      try {
        maintenance.runOnce();
      } catch (final IOException | RuntimeException e) {
        LOG.error("Maintenance failed, trying again in {}", interval, e);
      }
    } while (!stop.await(interval.toMillis(), TimeUnit.MILLISECONDS));
  }

  static StoreConfig storeConfig(final Settings settings) {
    return switch (StoreConfig.Type.of(settings.required(STORE_TYPE))) {
      case FILESYSTEM -> StoreConfig.filesystem(Path.of(settings.required(STORE_PATH)));
      case S3 ->
          StoreConfig.s3(
              new S3StoreConfig(
                  settings.required(STORE_BUCKET),
                  settings.optional(STORE_BASE_PATH),
                  settings.optional(STORE_REGION),
                  endpoint(settings),
                  settings.optional(STORE_ACCESS_KEY_ID),
                  settings.optional(STORE_SECRET_ACCESS_KEY),
                  settings.flag(STORE_FORCE_PATH_STYLE)));
    };
  }

  static MaintenanceConfig maintenanceConfig(final Settings settings) {
    final var rollupAge = settings.duration(ROLLUP_AGE);
    final var rollupTargetBytes = settings.number(ROLLUP_TARGET_BYTES);
    return new MaintenanceConfig(
        rollupAge != null ? rollupAge : MaintenanceConfig.DEFAULT_ROLLUP_AGE,
        rollupTargetBytes != null
            ? rollupTargetBytes
            : MaintenanceConfig.DEFAULT_ROLLUP_TARGET_BYTES,
        settings.duration(RETENTION));
  }

  static Duration interval(final Settings settings) {
    final var interval = settings.duration(INTERVAL);
    if (interval == null) {
      return DEFAULT_INTERVAL;
    }
    if (interval.isNegative() || interval.isZero()) {
      throw new InvalidMaintenanceConfigException(INTERVAL, "to be longer than 0", "" + interval);
    }
    return interval;
  }

  private static @Nullable URI endpoint(final Settings settings) {
    final var endpoint = settings.optional(STORE_ENDPOINT);
    if (endpoint == null) {
      return null;
    }
    try {
      return URI.create(endpoint);
    } catch (final IllegalArgumentException e) {
      throw new InvalidMaintenanceConfigException(
          STORE_ENDPOINT, "to be a URI", "'" + endpoint + "'");
    }
  }
}
