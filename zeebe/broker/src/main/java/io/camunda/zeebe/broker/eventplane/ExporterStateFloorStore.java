/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.eventplane;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.broker.exporter.stream.ExporterDirector;
import io.camunda.zeebe.broker.system.configuration.EventPlaneCfg;
import io.camunda.zeebe.eventplane.publisher.FloorStore;
import io.camunda.zeebe.eventplane.publisher.UploadFloor;
import java.io.IOException;
import java.util.UUID;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.Nullable;

/**
 * Keeps the upload floor as the exporter director's external position {@link
 * EventPlaneCfg#FLOOR_ID}: the position is the last uploaded record's, and the metadata names the
 * history and the index. Blocks on the director, so it must not run on an actor thread.
 */
final class ExporterStateFloorStore implements FloorStore {

  private static final ObjectMapper JSON = new ObjectMapper();

  private final ExporterDirector director;

  ExporterStateFloorStore(final ExporterDirector director) {
    this.director = director;
  }

  @Override
  public @Nullable UploadFloor load() throws IOException {
    final var position = director.getExternalPosition(EventPlaneCfg.FLOOR_ID).join();
    if (position.metadata().length == 0) {
      return null;
    }
    final var metadata = JSON.readValue(position.metadata(), FloorMetadata.class);
    return new UploadFloor(metadata.history(), metadata.index(), position.position());
  }

  @Override
  public void store(final UploadFloor floor) throws IOException {
    final var metadata = JSON.writeValueAsBytes(new FloorMetadata(floor.history(), floor.index()));
    director
        .setExternalPosition(EventPlaneCfg.FLOOR_ID, floor.position(), new UnsafeBuffer(metadata))
        .join();
  }

  private record FloorMetadata(UUID history, long index) {}
}
