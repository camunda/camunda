/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.history;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.camunda.zeebe.eventplane.history.HistoryManifest.Base;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.regex.Pattern;

/**
 * Reads and writes a {@link HistoryManifest} as JSON, in the field names and shape of the Event
 * Plane's design:
 *
 * <pre>{@code
 * {"version":1,"clusterId":"c","physicalTenantId":"default","historyId":"…",
 *  "createdAt":"2026-10-20T12:00:00Z",
 *  "partitions":{"3":{"sourceHistoryId":null,"index":0,"position":-1}}}
 * }</pre>
 *
 * The bytes are compact, the fields in this order, and the partitions in numerical order, so the
 * same manifest always has the same bytes, and writing it again changes nothing.
 */
public final class ManifestJson {

  private static final Pattern PARTITION = Pattern.compile("[1-9][0-9]{0,8}");

  private ManifestJson() {}

  /** The manifest as JSON, always the same bytes for the same manifest. */
  public static byte[] write(final HistoryManifest manifest) {
    final var root = JsonObject.MAPPER.createObjectNode();
    root.put("version", manifest.version());
    root.put("clusterId", manifest.clusterId());
    root.put("physicalTenantId", manifest.physicalTenantId());
    root.put("historyId", manifest.historyId().toString());
    root.put("createdAt", manifest.createdAt().toString());
    final var partitions = root.putObject("partitions");
    manifest
        .partitions()
        .forEach(
            (partition, base) -> {
              final var node = partitions.putObject(partition.toString());
              if (base.sourceHistoryId() == null) {
                node.putNull("sourceHistoryId");
              } else {
                node.put("sourceHistoryId", base.sourceHistoryId().toString());
              }
              node.put("index", base.index());
              node.put("position", base.position());
            });
    try {
      return JsonObject.MAPPER.writeValueAsBytes(root);
    } catch (final JsonProcessingException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Reads a manifest. Fields that it doesn't know are skipped.
   *
   * @throws InvalidHistoryJsonException if the JSON isn't that of a valid manifest
   * @throws IOException if the bytes aren't JSON
   */
  public static HistoryManifest read(final byte[] json) throws IOException {
    final var root = JsonObject.parse(json);
    final var partitions = root.object("partitions");
    final var bases = new HashMap<Integer, Base>();
    for (final var name : partitions.fieldNames()) {
      if (!PARTITION.matcher(name).matches()) {
        throw new InvalidHistoryJsonException(
            partitions.path(name), "a partition number of 1 or more as the key", name);
      }
      final var base = partitions.object(name);
      try {
        bases.put(
            Integer.parseInt(name),
            new Base(
                base.optionalUuid("sourceHistoryId"),
                base.longValue("index"),
                base.longValue("position")));
      } catch (final IllegalArgumentException e) {
        throw new InvalidHistoryJsonException(
            partitions.path(name), "a valid base", JsonObject.shown(json), e);
      }
    }
    try {
      return new HistoryManifest(
          root.intValue("version"),
          root.text("clusterId"),
          root.text("physicalTenantId"),
          root.uuid("historyId"),
          root.instant("createdAt"),
          bases);
    } catch (final IllegalArgumentException e) {
      throw new InvalidHistoryJsonException(
          "the manifest", "a valid history manifest", JsonObject.shown(json), e);
    }
  }
}
