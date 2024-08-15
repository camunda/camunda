/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.event.process;

import io.camunda.optimize.dto.optimize.OptimizeDto;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class EventDto implements OptimizeDto, EventProcessEventDto {

  @NotBlank
  private String id;
  @NotBlank
  private String eventName;

  @NotNull
  @Min(0)
  private Long timestamp;

  private Long ingestionTimestamp;
  @NotBlank
  private String traceId;
  private String group;
  private String source;
  private Object data;

  public EventDto(@NotBlank final String id, @NotBlank final String eventName,
      @NotNull @Min(0) final Long timestamp,
      final Long ingestionTimestamp, @NotBlank final String traceId, final String group,
      final String source, final Object data) {
    this.id = id;
    this.eventName = eventName;
    this.timestamp = timestamp;
    this.ingestionTimestamp = ingestionTimestamp;
    this.traceId = traceId;
    this.group = group;
    this.source = source;
    this.data = data;
  }

  public EventDto() {
  }

  protected EventDto(final EventDtoBuilder<?, ?> b) {
    id = b.id;
    eventName = b.eventName;
    timestamp = b.timestamp;
    ingestionTimestamp = b.ingestionTimestamp;
    traceId = b.traceId;
    group = b.group;
    source = b.source;
    data = b.data;
  }

  public @NotBlank String getId() {
    return id;
  }

  public void setId(@NotBlank final String id) {
    this.id = id;
  }

  public @NotBlank String getEventName() {
    return eventName;
  }

  public void setEventName(@NotBlank final String eventName) {
    this.eventName = eventName;
  }

  public @NotNull @Min(0) Long getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(@NotNull @Min(0) final Long timestamp) {
    this.timestamp = timestamp;
  }

  public Long getIngestionTimestamp() {
    return ingestionTimestamp;
  }

  public void setIngestionTimestamp(final Long ingestionTimestamp) {
    this.ingestionTimestamp = ingestionTimestamp;
  }

  public @NotBlank String getTraceId() {
    return traceId;
  }

  public void setTraceId(@NotBlank final String traceId) {
    this.traceId = traceId;
  }

  public String getGroup() {
    return group;
  }

  public void setGroup(final String group) {
    this.group = group;
  }

  public String getSource() {
    return source;
  }

  public void setSource(final String source) {
    this.source = source;
  }

  public Object getData() {
    return data;
  }

  public void setData(final Object data) {
    this.data = data;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof EventDto;
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
    if (!(o instanceof EventDto)) {
      return false;
    }
    final EventDto other = (EventDto) o;
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
    return "EventDto(id=" + getId() + ", eventName=" + getEventName() + ", timestamp="
        + getTimestamp() + ", ingestionTimestamp=" + getIngestionTimestamp()
        + ", traceId=" + getTraceId() + ", group=" + getGroup() + ", source="
        + getSource() + ")";
  }

  public static EventDtoBuilder<?, ?> builder() {
    return new EventDtoBuilderImpl();
  }

  public EventDtoBuilder<?, ?> toBuilder() {
    return new EventDtoBuilderImpl().$fillValuesFrom(this);
  }

  public static final class Fields {

    public static final String id = "id";
    public static final String eventName = "eventName";
    public static final String timestamp = "timestamp";
    public static final String ingestionTimestamp = "ingestionTimestamp";
    public static final String traceId = "traceId";
    public static final String group = "group";
    public static final String source = "source";
    public static final String data = "data";
  }

  public static abstract class EventDtoBuilder<C extends EventDto, B extends EventDtoBuilder<C, B>> {

    private @NotBlank String id;
    private @NotBlank String eventName;
    private @NotNull
    @Min(0) Long timestamp;
    private Long ingestionTimestamp;
    private @NotBlank String traceId;
    private String group;
    private String source;
    private Object data;

    public B id(@NotBlank final String id) {
      this.id = id;
      return self();
    }

    public B eventName(@NotBlank final String eventName) {
      this.eventName = eventName;
      return self();
    }

    public B timestamp(@NotNull @Min(0) final Long timestamp) {
      this.timestamp = timestamp;
      return self();
    }

    public B ingestionTimestamp(final Long ingestionTimestamp) {
      this.ingestionTimestamp = ingestionTimestamp;
      return self();
    }

    public B traceId(@NotBlank final String traceId) {
      this.traceId = traceId;
      return self();
    }

    public B group(final String group) {
      this.group = group;
      return self();
    }

    public B source(final String source) {
      this.source = source;
      return self();
    }

    public B data(final Object data) {
      this.data = data;
      return self();
    }

    private static void $fillValuesFromInstanceIntoBuilder(final EventDto instance,
        final EventDtoBuilder<?, ?> b) {
      b.id(instance.id);
      b.eventName(instance.eventName);
      b.timestamp(instance.timestamp);
      b.ingestionTimestamp(instance.ingestionTimestamp);
      b.traceId(instance.traceId);
      b.group(instance.group);
      b.source(instance.source);
      b.data(instance.data);
    }

    protected B $fillValuesFrom(final C instance) {
      EventDtoBuilder.$fillValuesFromInstanceIntoBuilder(instance, this);
      return self();
    }

    protected abstract B self();

    public abstract C build();

    @Override
    public String toString() {
      return "EventDto.EventDtoBuilder(id=" + id + ", eventName=" + eventName
          + ", timestamp=" + timestamp + ", ingestionTimestamp=" + ingestionTimestamp
          + ", traceId=" + traceId + ", group=" + group + ", source=" + source
          + ", data=" + data + ")";
    }
  }

  private static final class EventDtoBuilderImpl extends
      EventDtoBuilder<EventDto, EventDtoBuilderImpl> {

    private EventDtoBuilderImpl() {
    }

    @Override
    protected EventDtoBuilderImpl self() {
      return this;
    }
    
    @Override
    public EventDto build() {
      return new EventDto(this);
    }
  }
}
