/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.engine.service.zeebe;

import io.camunda.optimize.dto.optimize.OrdinalDto;
import io.camunda.optimize.dto.zeebe.ZeebeGenericRecordDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.writer.OrdinalWriter;
import io.camunda.optimize.service.importing.zeebe.cache.OrdinalCache;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.OrdinalIntent;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;

/**
 * Processes {@link ValueType#ORDINAL} / {@link OrdinalIntent#TICKED} records from the combined
 * Zeebe index, writes them to the ordinal index in Elasticsearch, and updates the in-memory {@link
 * OrdinalCache} so that subsequent flat-index imports can derive time-based index names.
 */
public class ZeebeOrdinalImportService {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(ZeebeOrdinalImportService.class);

  private final OrdinalCache ordinalCache;
  private final OrdinalWriter ordinalWriter;
  private final DatabaseClient databaseClient;
  private final ConfigurationService configurationService;

  public ZeebeOrdinalImportService(
      final OrdinalCache ordinalCache,
      final OrdinalWriter ordinalWriter,
      final DatabaseClient databaseClient,
      final ConfigurationService configurationService) {
    this.ordinalCache = ordinalCache;
    this.ordinalWriter = ordinalWriter;
    this.databaseClient = databaseClient;
    this.configurationService = configurationService;
  }

  /**
   * Processes a batch of generic Zeebe records, filtering for ORDINAL/TICKED events, persisting
   * them to the ordinal index, and updating the in-memory cache.
   *
   * @param records the batch of generic Zeebe records from the combined index
   */
  public void processOrdinalRecords(final List<ZeebeGenericRecordDto> records) {
    final List<OrdinalDto> ordinals = new ArrayList<>();
    for (final ZeebeGenericRecordDto record : records) {
      if (!ValueType.ORDINAL.equals(record.getValueType())) {
        continue;
      }
      if (!OrdinalIntent.TICKED.name().equals(record.getIntent())) {
        continue;
      }
      final Map<String, Object> value = record.getValue();
      if (value == null) {
        continue;
      }
      final Object rawOrdinal = value.get("ordinal");
      if (rawOrdinal == null) {
        continue;
      }
      final int ordinalValue;
      try {
        ordinalValue = toInt(rawOrdinal);
      } catch (final IllegalArgumentException e) {
        LOG.warn(
            "Skipping ORDINAL/TICKED record with unexpected ordinal value type: {}",
            e.getMessage());
        continue;
      }
      final long timestampMs = record.getTimestamp();
      final OffsetDateTime dateTime =
          OffsetDateTime.ofInstant(Instant.ofEpochMilli(timestampMs), ZoneId.systemDefault());

      ordinals.add(new OrdinalDto(ordinalValue, timestampMs, dateTime));
      ordinalCache.update(ordinalValue, timestampMs);
    }

    if (!ordinals.isEmpty()) {
      try {
        databaseClient.executeImportRequestsAsBulk(
            "ordinals",
            ordinalWriter.generateOrdinalImports(ordinals),
            configurationService.getSkipDataAfterNestedDocLimitReached());
        LOG.debug("Indexed {} ordinal tick(s).", ordinals.size());
      } catch (final Exception e) {
        LOG.error("Failed to index {} ordinal tick(s): {}", ordinals.size(), e.getMessage(), e);
      }
    }
  }

  private static int toInt(final Object value) {
    if (value instanceof final Number n) {
      return n.intValue();
    }
    throw new IllegalArgumentException(
        "Cannot convert ordinal value to int: unexpected type "
            + (value == null ? "null" : value.getClass().getName()));
  }
}
