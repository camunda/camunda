/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.stream;

import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.logstreams.log.LoggedEvent;
import io.camunda.zeebe.protocol.impl.encoding.RecordMetadataBlock;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.stream.api.EventFilter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;

/**
 * A coarse, metadata-only filter (record type / value type / intent) used to decide whether an
 * exporter should even attempt to process a record, before it is fully deserialized.
 */
final class ExporterEventFilter implements EventFilter {

  private static final Logger LOG = Loggers.EXPORTER_LOGGER;

  private final RecordMetadataBlock decoder = new RecordMetadataBlock();
  private final Map<RecordType, Boolean> acceptRecordTypes;
  private final Map<ValueType, Boolean> acceptValueTypes;
  private final Map<Intent, Boolean> acceptIntents;

  private ExporterEventFilter(
      final Map<RecordType, Boolean> acceptRecordTypes,
      final Map<ValueType, Boolean> acceptValueTypes,
      final Map<Intent, Boolean> acceptIntents) {
    this.acceptRecordTypes = acceptRecordTypes;
    this.acceptValueTypes = acceptValueTypes;
    this.acceptIntents = acceptIntents;
  }

  static ExporterEventFilter forSingleContainer(final ExporterContainer container) {
    final List<Context.RecordFilter> recordFilters = List.of(container.getContext().getFilter());

    final Map<RecordType, Boolean> acceptRecordTypes =
        Arrays.stream(RecordType.values())
            .collect(
                Collectors.toMap(
                    Function.identity(),
                    type -> recordFilters.stream().anyMatch(f -> f.acceptType(type))));

    final Map<ValueType, Boolean> acceptValueTypes =
        Arrays.stream(ValueType.values())
            .collect(
                Collectors.toMap(
                    Function.identity(),
                    type -> recordFilters.stream().anyMatch(f -> f.acceptValue(type))));

    final Map<Intent, Boolean> acceptIntents =
        Intent.INTENT_CLASSES.stream()
            .flatMap(i -> Arrays.stream(i.getEnumConstants()))
            .collect(
                Collectors.toMap(
                    Function.identity(),
                    intent -> recordFilters.stream().anyMatch(f -> f.acceptIntent(intent))));

    return new ExporterEventFilter(acceptRecordTypes, acceptValueTypes, acceptIntents);
  }

  @Override
  public boolean applies(final LoggedEvent event) {
    decoder.wrap(event.getMetadata(), event.getMetadataOffset());

    final RecordType recordType = decoder.recordType();
    final ValueType valueType = decoder.valueType();
    final Intent intent = decoder.intent();

    try {
      return acceptRecordTypes.get(recordType)
          && acceptValueTypes.get(valueType)
          && acceptIntents.get(intent);
    } catch (final NullPointerException e) {
      // Log added to root cause https://github.com/camunda/camunda/issues/36621
      LOG.error(
          """
              NPE when applying event filter for event: {}
              - recordType={}, valueType={}, intent={}
              - acceptRecordTypes: {}
              - acceptValueTypes: {}
              - acceptIntents: {}""",
          event,
          recordType,
          valueType,
          intent,
          acceptRecordTypes,
          acceptValueTypes,
          acceptIntents.entrySet().stream()
              .map(
                  entry -> {
                    final var key = entry.getKey();
                    if (key == null) {
                      return "null: %s".formatted(entry.getValue());
                    }
                    return String.format(
                        "%s.%s: %s", key.getClass().getSimpleName(), key, entry.getValue());
                  })
              .toList());
      throw e;
    }
  }

  @Override
  public String toString() {
    return "ExporterEventFilter{"
        + "acceptRecordTypes="
        + acceptRecordTypes
        + ", acceptValueTypes="
        + acceptValueTypes
        + ", acceptIntents="
        + acceptIntents
        + '}';
  }
}
