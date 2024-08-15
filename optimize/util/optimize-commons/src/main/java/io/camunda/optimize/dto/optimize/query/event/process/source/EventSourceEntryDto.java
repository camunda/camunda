/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.event.process.source;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.optimize.service.util.IdGenerator;
import jakarta.validation.constraints.NotNull;
import java.util.Optional;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = ExternalEventSourceEntryDto.class, name = "external"),
})
public abstract class EventSourceEntryDto<CONFIG extends EventSourceConfigDto> {

  public static final String TYPE = "type";
  protected String id = IdGenerator.getNextId();

  @NotNull protected CONFIG configuration;

  public EventSourceEntryDto() {}

  protected EventSourceEntryDto(final EventSourceEntryDtoBuilder<CONFIG, ?, ?> b) {
    if (b.id$set) {
      id = b.id$value;
    } else {
      id = $default$id();
    }
    configuration = b.configuration;
  }

  @JsonIgnore
  public abstract EventSourceType getSourceType();

  // This source identifier is only used internally by Optimize for logic such as autogeneration
  @JsonIgnore
  public String getSourceIdentifier() {
    final ExternalEventSourceConfigDto externalSourceConfig =
        (ExternalEventSourceConfigDto) configuration;
    if (externalSourceConfig.isIncludeAllGroups()) {
      return getSourceType() + ":" + "optimize_allExternalEventGroups";
    } else {
      return getSourceType()
          + ":"
          + Optional.ofNullable(externalSourceConfig.getGroup())
              .orElse("optimize_noGroupSpecified");
    }
  }

  public String getId() {
    return id;
  }

  public void setId(final String id) {
    if (id == null) {
      throw new IllegalArgumentException("id cannot be null");
    }

    this.id = id;
  }

  public @NotNull CONFIG getConfiguration() {
    return configuration;
  }

  public void setConfiguration(@NotNull final CONFIG configuration) {
    this.configuration = configuration;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof EventSourceEntryDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $id = getId();
    result = result * PRIME + ($id == null ? 43 : $id.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof EventSourceEntryDto)) {
      return false;
    }
    final EventSourceEntryDto<?> other = (EventSourceEntryDto<?>) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$id = getId();
    final Object other$id = other.getId();
    if (this$id == null ? other$id != null : !this$id.equals(other$id)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "EventSourceEntryDto(id=" + getId() + ", configuration=" + getConfiguration() + ")";
  }

  private static String $default$id() {
    return IdGenerator.getNextId();
  }

  public static final class Fields {

    public static final String id = "id";
    public static final String configuration = "configuration";
  }

  public abstract static class EventSourceEntryDtoBuilder<
      CONFIG extends EventSourceConfigDto,
      C extends EventSourceEntryDto<CONFIG>,
      B extends EventSourceEntryDtoBuilder<CONFIG, C, B>> {

    private String id$value;
    private boolean id$set;
    private @NotNull CONFIG configuration;

    public B id(final String id) {
      if (id == null) {
        throw new IllegalArgumentException("id cannot be null");
      }

      id$value = id;
      id$set = true;
      return self();
    }

    public B configuration(@NotNull final CONFIG configuration) {
      this.configuration = configuration;
      return self();
    }

    private static <CONFIG extends EventSourceConfigDto> void $fillValuesFromInstanceIntoBuilder(
        final EventSourceEntryDto<CONFIG> instance,
        final EventSourceEntryDtoBuilder<CONFIG, ?, ?> b) {
      b.id(instance.id);
      b.configuration(instance.configuration);
    }

    protected B $fillValuesFrom(final C instance) {
      EventSourceEntryDtoBuilder.$fillValuesFromInstanceIntoBuilder(instance, this);
      return self();
    }

    protected abstract B self();

    public abstract C build();

    @Override
    public String toString() {
      return "EventSourceEntryDto.EventSourceEntryDtoBuilder(id$value="
          + id$value
          + ", configuration="
          + configuration
          + ")";
    }
  }
}
