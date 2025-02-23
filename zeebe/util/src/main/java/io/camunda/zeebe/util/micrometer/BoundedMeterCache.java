/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.micrometer;

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.RemovalListener;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Meter.MeterProvider;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.AbstractMap;
import java.util.Set;
import java.util.function.Consumer;

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
public final class BoundedMeterCache<T extends Meter> extends AbstractMap<String, T> {

  private final LoadingCache<String, T> meters;

  private BoundedMeterCache(final LoadingCache<String, T> meters) {
    this.meters = meters;
  }

  /**
   * Returns the meter for given tag value. Note that if it doesn't exist, it's loaded using the
   * provided cache loader at construction time.
   *
   * @param tagValue the tag value
   * @return the matching meter
   */
  public T get(final String tagValue) {
    return meters.get(tagValue);
  }

  @SuppressWarnings("NullableProblems")
  @Override
  public Set<Entry<String, T>> entrySet() {
    return meters.asMap().entrySet();
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

  /**
   * Builds a bounded meter cache. By default, it sets the maximum size to 100, and expires/removes
   * meters that haven't been accessed in the last.
   *
   * <p>The entries are automatically loaded on access based on the given {@link MeterProvider}
   *
   * <p>NOTE: this can be overridden and customized via {@link #config(Consumer)}
   *
   * @param <T> the expected meter type
   */
  public static final class Builder<T extends Meter> {
    private final CacheLoader<String, T> loader;
    private final Caffeine<String, T> builder;

    private int maxSize = 500;
    private Duration expiry = Duration.ofMinutes(5);

    public Builder(
        final MeterRegistry registry, final MeterProvider<T> provider, final KeyName tagKey) {
      this(registry, provider, tagKey.asString());
    }

    public Builder(
        final MeterRegistry registry, final MeterProvider<T> provider, final String tagKey) {
      loader = tagValue -> provider.withTag(tagKey, tagValue);
      builder = Caffeine.newBuilder().evictionListener(removeEvictedMeter(registry));
    }

    /**
     * Sets the maximum number of entries for this cache, after which entries are evicted based on
     * least-frequent usage.
     *
     * <p>Defaults to 500.
     *
     * @param maxSize the maximum number of entries in the cache
     * @return this builder for chaining
     */
    public Builder<T> maxSize(final int maxSize) {
      this.maxSize = maxSize;
      return this;
    }

    /**
     * Sets the TTL on a given cache entry, from the time it was last accessed.
     *
     * <p>Defaults to 5 minutes.
     *
     * @param expiry the duration after which an entry that hasn't been accessed should be removed
     * @return this builder for chaining
     */
    public Builder<T> ttl(final Duration expiry) {
      this.expiry = expiry;
      return this;
    }

    /**
     * Customizes the underlying {@link Caffeine} cache, e.g. setting a maximum size, settings a
     * TTL, etc.
     *
     * @param modifier the callback modifying the cache
     * @return this builder for chaining
     */
    public Builder<T> config(final Consumer<Caffeine<String, T>> modifier) {
      modifier.accept(builder);
      return this;
    }

    public BoundedMeterCache<T> build() {
      return new BoundedMeterCache<>(
          builder.maximumSize(maxSize).expireAfterAccess(expiry).build(loader));
    }
  }
}
