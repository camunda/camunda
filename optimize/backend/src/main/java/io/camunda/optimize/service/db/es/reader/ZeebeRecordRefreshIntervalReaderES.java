/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.reader;

import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch.indices.IndexSettings;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.reader.ZeebeRecordRefreshIntervalReader;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.time.Duration;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class ZeebeRecordRefreshIntervalReaderES implements ZeebeRecordRefreshIntervalReader {

  private static final Logger LOG =
      LoggerFactory.getLogger(ZeebeRecordRefreshIntervalReaderES.class);
  private static final Pattern TIME_VALUE = Pattern.compile("(\\d+)(ms|s|m|h|d)");

  private final OptimizeElasticsearchClient esClient;

  public ZeebeRecordRefreshIntervalReaderES(final OptimizeElasticsearchClient esClient) {
    this.esClient = esClient;
  }

  @Override
  public Duration getRefreshInterval(final String indexAlias) {
    try {
      return esClient
          .esWithTransportOptions()
          .indices()
          .getSettings(
              r ->
                  r.index(indexAlias)
                      .name("index.refresh_interval")
                      .includeDefaults(true)
                      .ignoreUnavailable(true)
                      .allowNoIndices(true))
          .result()
          .values()
          .stream()
          .flatMap(
              state ->
                  java.util.stream.Stream.of(state.settings(), state.defaults())
                      .filter(Objects::nonNull)
                      .map(ZeebeRecordRefreshIntervalReaderES::refreshInterval)
                      .filter(Objects::nonNull)
                      .limit(1))
          .map(ZeebeRecordRefreshIntervalReaderES::parse)
          .max(Duration::compareTo)
          .orElse(DEFAULT_REFRESH_INTERVAL);
    } catch (final Exception e) {
      LOG.warn(
          "Could not read the refresh interval of {}; assuming {}",
          indexAlias,
          DEFAULT_REFRESH_INTERVAL,
          e);
      return DEFAULT_REFRESH_INTERVAL;
    }
  }

  private static Time refreshInterval(final IndexSettings settings) {
    final IndexSettings index = settings.index() != null ? settings.index() : settings;
    return index.refreshInterval();
  }

  /** A refresh interval of -1 disables refreshes; the default is the safest guess then. */
  static Duration parse(final Time time) {
    final String value = time.isTime() ? time.time() : String.valueOf(time.offset());
    final Matcher matcher = TIME_VALUE.matcher(value.trim());
    if (!matcher.matches()) {
      return DEFAULT_REFRESH_INTERVAL;
    }
    final long amount = Long.parseLong(matcher.group(1));
    return switch (matcher.group(2)) {
      case "ms" -> Duration.ofMillis(amount);
      case "s" -> Duration.ofSeconds(amount);
      case "m" -> Duration.ofMinutes(amount);
      case "h" -> Duration.ofHours(amount);
      default -> Duration.ofDays(amount);
    };
  }
}
