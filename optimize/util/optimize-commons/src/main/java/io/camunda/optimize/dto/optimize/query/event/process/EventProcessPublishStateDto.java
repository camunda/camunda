/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.event.process;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EventProcessPublishStateDto {

  private String id;
  private String processMappingId;
  private String name;
  private OffsetDateTime publishDateTime;
  private EventProcessState state;
  private Double publishProgress;
  private Boolean deleted = false;
  private String xml;
  private Map<String, EventMappingDto> mappings;
  private List<EventImportSourceDto> eventImportSources = new ArrayList<>();

  public EventProcessPublishStateDto(final String id, final String processMappingId,
      final String name,
      final OffsetDateTime publishDateTime, final EventProcessState state,
      final Double publishProgress, final
  Boolean deleted, final String xml, final Map<String, EventMappingDto> mappings,
      final List<EventImportSourceDto> eventImportSources) {
    this.id = id;
    this.processMappingId = processMappingId;
    this.name = name;
    this.publishDateTime = publishDateTime;
    this.state = state;
    this.publishProgress = publishProgress;
    this.deleted = deleted;
    this.xml = xml;
    this.mappings = mappings;
    this.eventImportSources = eventImportSources;
  }

  protected EventProcessPublishStateDto() {
  }

  @JsonIgnore
  public String getProcessKey() {
    return processMappingId;
  }

  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public String getProcessMappingId() {
    return processMappingId;
  }

  public void setProcessMappingId(final String processMappingId) {
    this.processMappingId = processMappingId;
  }

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public OffsetDateTime getPublishDateTime() {
    return publishDateTime;
  }

  public void setPublishDateTime(final OffsetDateTime publishDateTime) {
    this.publishDateTime = publishDateTime;
  }

  public EventProcessState getState() {
    return state;
  }

  public void setState(final EventProcessState state) {
    this.state = state;
  }

  public Double getPublishProgress() {
    return publishProgress;
  }

  public void setPublishProgress(final Double publishProgress) {
    this.publishProgress = publishProgress;
  }

  public Boolean getDeleted() {
    return deleted;
  }

  public void setDeleted(final Boolean deleted) {
    this.deleted = deleted;
  }

  public String getXml() {
    return xml;
  }

  public void setXml(final String xml) {
    this.xml = xml;
  }

  public Map<String, EventMappingDto> getMappings() {
    return mappings;
  }

  public void setMappings(final Map<String, EventMappingDto> mappings) {
    this.mappings = mappings;
  }

  public List<EventImportSourceDto> getEventImportSources() {
    return eventImportSources;
  }

  public void setEventImportSources(final List<EventImportSourceDto> eventImportSources) {
    this.eventImportSources = eventImportSources;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof EventProcessPublishStateDto;
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
    if (!(o instanceof EventProcessPublishStateDto)) {
      return false;
    }
    final EventProcessPublishStateDto other = (EventProcessPublishStateDto) o;
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
    return "EventProcessPublishStateDto(id=" + getId() + ", processMappingId="
        + getProcessMappingId() + ", name=" + getName() + ", publishDateTime="
        + getPublishDateTime() + ", state=" + getState() + ", publishProgress="
        + getPublishProgress() + ", deleted=" + getDeleted() + ", xml=" + getXml()
        + ", mappings=" + getMappings() + ", eventImportSources="
        + getEventImportSources() + ")";
  }

  private static Boolean $default$deleted() {
    return false;
  }

  private static List<EventImportSourceDto> $default$eventImportSources() {
    return new ArrayList<>();
  }

  public static EventProcessPublishStateDtoBuilder builder() {
    return new EventProcessPublishStateDtoBuilder();
  }

  public static final class Fields {

    public static final String id = "id";
    public static final String processMappingId = "processMappingId";
    public static final String name = "name";
    public static final String publishDateTime = "publishDateTime";
    public static final String state = "state";
    public static final String publishProgress = "publishProgress";
    public static final String deleted = "deleted";
    public static final String xml = "xml";
    public static final String mappings = "mappings";
    public static final String eventImportSources = "eventImportSources";
  }

  public static class EventProcessPublishStateDtoBuilder {

    private String id;
    private String processMappingId;
    private String name;
    private OffsetDateTime publishDateTime;
    private EventProcessState state;
    private Double publishProgress;
    private Boolean deleted$value;
    private boolean deleted$set;
    private String xml;
    private Map<String, EventMappingDto> mappings;
    private List<EventImportSourceDto> eventImportSources$value;
    private boolean eventImportSources$set;

    EventProcessPublishStateDtoBuilder() {
    }

    public EventProcessPublishStateDtoBuilder id(final String id) {
      this.id = id;
      return this;
    }

    public EventProcessPublishStateDtoBuilder processMappingId(final String processMappingId) {
      this.processMappingId = processMappingId;
      return this;
    }

    public EventProcessPublishStateDtoBuilder name(final String name) {
      this.name = name;
      return this;
    }

    public EventProcessPublishStateDtoBuilder publishDateTime(
        final OffsetDateTime publishDateTime) {
      this.publishDateTime = publishDateTime;
      return this;
    }

    public EventProcessPublishStateDtoBuilder state(final EventProcessState state) {
      this.state = state;
      return this;
    }

    public EventProcessPublishStateDtoBuilder publishProgress(final Double publishProgress) {
      this.publishProgress = publishProgress;
      return this;
    }

    public EventProcessPublishStateDtoBuilder deleted(final Boolean deleted) {
      deleted$value = deleted;
      deleted$set = true;
      return this;
    }

    public EventProcessPublishStateDtoBuilder xml(final String xml) {
      this.xml = xml;
      return this;
    }

    public EventProcessPublishStateDtoBuilder mappings(
        final Map<String, EventMappingDto> mappings) {
      this.mappings = mappings;
      return this;
    }

    public EventProcessPublishStateDtoBuilder eventImportSources(
        final List<EventImportSourceDto> eventImportSources) {
      eventImportSources$value = eventImportSources;
      eventImportSources$set = true;
      return this;
    }

    public EventProcessPublishStateDto build() {
      Boolean deleted$value = this.deleted$value;
      if (!deleted$set) {
        deleted$value = EventProcessPublishStateDto.$default$deleted();
      }
      List<EventImportSourceDto> eventImportSources$value = this.eventImportSources$value;
      if (!eventImportSources$set) {
        eventImportSources$value = EventProcessPublishStateDto.$default$eventImportSources();
      }
      return new EventProcessPublishStateDto(id, processMappingId, name,
          publishDateTime, state, publishProgress, deleted$value, xml,
          mappings, eventImportSources$value);
    }

    @Override
    public String toString() {
      return "EventProcessPublishStateDto.EventProcessPublishStateDtoBuilder(id=" + id
          + ", processMappingId=" + processMappingId + ", name=" + name
          + ", publishDateTime=" + publishDateTime + ", state=" + state
          + ", publishProgress=" + publishProgress + ", deleted$value=" + deleted$value
          + ", xml=" + xml + ", mappings=" + mappings + ", eventImportSources$value="
          + eventImportSources$value + ")";
    }
  }
}
