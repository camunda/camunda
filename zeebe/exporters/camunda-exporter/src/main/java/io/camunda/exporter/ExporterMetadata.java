/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.camunda.webapps.schema.entities.usertask.TaskEntity.TaskImplementation;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_DEFAULT)
public final class ExporterMetadata {
  private static final Logger LOGGER = LoggerFactory.getLogger(ExporterMetadata.class);
  private static final AtomicLongFieldUpdater<ExporterMetadata> INCIDENT_POSITION_SETTER =
      AtomicLongFieldUpdater.newUpdater(ExporterMetadata.class, "lastIncidentUpdatePosition");
  private static final int UNSET_POSITION = -1;

  @JsonIgnore private final ObjectWriter objectWriter;
  @JsonIgnore private final ObjectReader objectReader;
  private volatile long lastIncidentUpdatePosition = UNSET_POSITION;
  private Map<TaskImplementation, Long> firstUserTaskKeys =
      new HashMap<>() {
        {
          put(TaskImplementation.ZEEBE_USER_TASK, (long) UNSET_POSITION);
          put(TaskImplementation.JOB_WORKER, (long) UNSET_POSITION);
        }
      };

  public ExporterMetadata(final ObjectMapper objectMapper) {
    // Specialized reader/writer for this class for efficiency
    objectWriter = objectMapper.writerFor(ExporterMetadata.class);
    objectReader = objectMapper.readerForUpdating(this);
  }

  public long getLastIncidentUpdatePosition() {
    return lastIncidentUpdatePosition;
  }

  public void setLastIncidentUpdatePosition(final long newLastIncidentUpdatePosition) {
    INCIDENT_POSITION_SETTER.updateAndGet(
        this,
        prev -> updateLastIncidentUpdatePositionMonotonic(newLastIncidentUpdatePosition, prev));
  }

  public long getFirstUserTaskKey(final TaskImplementation implementation) {
    return firstUserTaskKeys.get(implementation);
  }

  public void setFirstUserTaskKey(final TaskImplementation implementation, final long userTaskKey) {
    if (firstUserTaskKeys.get(implementation) == UNSET_POSITION) {
      firstUserTaskKeys.put(implementation, userTaskKey);
    }
  }

  public Map<TaskImplementation, Long> getFirstUserTaskKeys() {
    return firstUserTaskKeys;
  }

  public void setFirstUserTaskKeys(final Map<TaskImplementation, Long> firstUserTaskKeys) {
    this.firstUserTaskKeys = firstUserTaskKeys;
  }

  public void deserialize(final byte[] bytes) {
    try {
      objectReader.readValue(bytes);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  // TODO: cache serialized version and only re-serialize if values have changed
  public byte[] serialize() {
    try {
      return objectWriter.writeValueAsBytes(this);
    } catch (final JsonProcessingException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public int hashCode() {
    return Objects.hash(lastIncidentUpdatePosition, firstUserTaskKeys);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ExporterMetadata that = (ExporterMetadata) o;
    return lastIncidentUpdatePosition == that.lastIncidentUpdatePosition
        && firstUserTaskKeys == that.firstUserTaskKeys;
  }

  @Override
  public String toString() {
    return "ExporterMetadata{"
        + "lastIncidentUpdatePosition="
        + lastIncidentUpdatePosition
        + ", firstUserTaskKeys="
        + firstUserTaskKeys
        + '}';
  }

  private long updateLastIncidentUpdatePositionMonotonic(
      final long newLastIncidentUpdatePosition, final long prev) {
    if (prev > newLastIncidentUpdatePosition) {
      LOGGER.warn(
          """
          Expected to update the last incident update position {} to a greater value, but got {}; \
          will ignore this update, but this could indicate a bug""",
          prev,
          newLastIncidentUpdatePosition);
      return prev;
    }

    return newLastIncidentUpdatePosition;
  }
}
