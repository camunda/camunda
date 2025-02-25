/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.micrometer;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.RemovalListener;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Meter.MeterProvider;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.AbstractMap;

/**
 * A wrapper around {@link Caffeine} for when you want to cache metrics with high cardinality tags
 * (i.e. based on user input, like job type), but don't want to run out of memory.
 *
 * <p>This not only gives you the benefit of keeping your local cache trimmed down, but it will also
 * prevent the underlying {@link MeterRegistry} from growing too much due to high cardinality tags,
 * as it will remove the related meter when a tag is evicted.
 *
 * <p>NOTE: the class extends {@link AbstractMap} and implements {@link java.util.Map}, so that you
 * can use it with things like {@link io.camunda.zeebe.util.collection.Table}, {@link
 * io.camunda.zeebe.util.collection.Map3D}, etc.
 *
 * @param <T> the expected meter type
 */
public final class BoundedMeterCache<T extends Meter> {

  private final LoadingCache<String, T> meters;

  private BoundedMeterCache(final LoadingCache<String, T> meters) {
    this.meters = meters;
  }

  /**
   * Returns a bounded cache which will load the meter from the given provider based on the tag key
   * given here, and the value given when accessing the cache via {@link #get(String)}.
   *
   * <p>By default, the cache is bounded to a maximum of 500 entries.
   *
   * @param registry the registry to load from
   * @param provider the meter provider which gets or creates a meter when not in the cache
   * @param tagKey the tag key to pass to the meter provider
   * @return a new bounded cache
   * @param <T> the expected meter type
   */
  public static <T extends Meter> BoundedMeterCache<T> of(
      final MeterRegistry registry, final MeterProvider<T> provider, final KeyName tagKey) {
    return of(registry, provider, tagKey, 500);
  }

  /**
   * Returns a bounded cache which will load the meter from the given provider based on the tag key
   * given here, and the value given when accessing the cache via {@link #get(String)}.
   *
   * <p>Note that the entries are evicted using a least-frequently-used
   *
   * @param registry the registry to load from
   * @param provider the meter provider which gets or creates a meter when not in the cache
   * @param tagKey the tag key to pass to the meter provider
   * @param maxSize the maximum number of keys in this cache before eviction occurs
   * @return a new bounded cache
   * @param <T> the expected meter type
   */
  public static <T extends Meter> BoundedMeterCache<T> of(
      final MeterRegistry registry,
      final MeterProvider<T> provider,
      final KeyName tagKey,
      final int maxSize) {
    final var builder =
        Caffeine.newBuilder().maximumSize(maxSize).evictionListener(removeEvictedMeter(registry));

    return new BoundedMeterCache<>(builder.build(v -> provider.withTag(tagKey.asString(), v)));
  }

  /**
   * Returns the meter for given tag value.
   *
   * @param tagValue the tag value
   * @return the matching meter
   */
  public T get(final String tagValue) {
    return meters.get(tagValue);
  }

  private static <K, T extends Meter> RemovalListener<K, T> removeEvictedMeter(
      final MeterRegistry registry) {
    return (labels, gauge, ignored) -> removeEvictedMeter(registry, gauge);
  }

  private static <T extends Meter> void removeEvictedMeter(
      final MeterRegistry registry, final T value) {
    if (value != null) {
      registry.remove(value);
    }
  }
}
