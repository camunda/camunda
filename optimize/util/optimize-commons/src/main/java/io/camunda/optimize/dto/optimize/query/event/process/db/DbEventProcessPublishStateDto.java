/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.event.process.db;

import io.camunda.optimize.dto.optimize.query.event.process.EventImportSourceDto;
import io.camunda.optimize.dto.optimize.query.event.process.EventMappingDto;
import io.camunda.optimize.dto.optimize.query.event.process.EventProcessPublishStateDto;
import io.camunda.optimize.dto.optimize.query.event.process.EventProcessState;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class DbEventProcessPublishStateDto {

  private String id;
  private String processMappingId;
  private String name;
  private OffsetDateTime publishDateTime;
  private EventProcessState state;
  private Double publishProgress;
  private Boolean deleted = false;
  private String xml;
  private List<DbEventMappingDto> mappings;
  private List<EventImportSourceDto> eventImportSources;

  public DbEventProcessPublishStateDto(
      final String id,
      final String processMappingId,
      final String name,
      final OffsetDateTime publishDateTime,
      final EventProcessState state,
      final Double publishProgress,
      final Boolean deleted,
      final String xml,
      final List<DbEventMappingDto> mappings,
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

  protected DbEventProcessPublishStateDto() {}

  public static DbEventProcessPublishStateDto fromEventProcessPublishStateDto(
      final EventProcessPublishStateDto publishState) {
    return DbEventProcessPublishStateDto.builder()
        .id(publishState.getId())
        .processMappingId(publishState.getProcessMappingId())
        .name(publishState.getName())
        .xml(publishState.getXml())
        .publishDateTime(publishState.getPublishDateTime())
        .state(publishState.getState())
        .publishProgress(publishState.getPublishProgress())
        .deleted(publishState.getDeleted())
        .mappings(
            Optional.ofNullable(publishState.getMappings())
                .map(
                    mappings ->
                        mappings.keySet().stream()
                            .map(
                                flowNodeId ->
                                    DbEventMappingDto.fromEventMappingDto(
                                        flowNodeId, publishState.getMappings().get(flowNodeId)))
                            .collect(Collectors.toList()))
                .orElse(null))
        .eventImportSources(publishState.getEventImportSources())
        .build();
  }

  public EventProcessPublishStateDto toEventProcessPublishStateDto() {
    return EventProcessPublishStateDto.builder()
        .id(getId())
        .processMappingId(getProcessMappingId())
        .name(getName())
        .xml(getXml())
        .publishDateTime(getPublishDateTime())
        .state(getState())
        .publishProgress(getPublishProgress())
        .deleted(getDeleted())
        .mappings(
            Optional.ofNullable(mappings)
                .map(
                    mappingList ->
                        mappingList.stream()
                            .collect(
                                Collectors.toMap(
                                    DbEventMappingDto::getFlowNodeId,
                                    mapping ->
                                        EventMappingDto.builder()
                                            .start(mapping.getStart())
                                            .end(mapping.getEnd())
                                            .build())))
                .orElse(null))
        .eventImportSources(getEventImportSources())
        .build();
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

  public List<DbEventMappingDto> getMappings() {
    return mappings;
  }

  public void setMappings(final List<DbEventMappingDto> mappings) {
    this.mappings = mappings;
  }

  public List<EventImportSourceDto> getEventImportSources() {
    return eventImportSources;
  }

  public void setEventImportSources(final List<EventImportSourceDto> eventImportSources) {
    this.eventImportSources = eventImportSources;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof DbEventProcessPublishStateDto;
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
    if (!(o instanceof DbEventProcessPublishStateDto)) {
      return false;
    }
    final DbEventProcessPublishStateDto other = (DbEventProcessPublishStateDto) o;
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
    return "DbEventProcessPublishStateDto(id="
        + getId()
        + ", processMappingId="
        + getProcessMappingId()
        + ", name="
        + getName()
        + ", publishDateTime="
        + getPublishDateTime()
        + ", state="
        + getState()
        + ", publishProgress="
        + getPublishProgress()
        + ", deleted="
        + getDeleted()
        + ", xml="
        + getXml()
        + ", mappings="
        + getMappings()
        + ", eventImportSources="
        + getEventImportSources()
        + ")";
  }

  private static Boolean $default$deleted() {
    return false;
  }

  public static DbEventProcessPublishStateDtoBuilder builder() {
    return new DbEventProcessPublishStateDtoBuilder();
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

  public static class DbEventProcessPublishStateDtoBuilder {

    private String id;
    private String processMappingId;
    private String name;
    private OffsetDateTime publishDateTime;
    private EventProcessState state;
    private Double publishProgress;
    private Boolean deleted$value;
    private boolean deleted$set;
    private String xml;
    private List<DbEventMappingDto> mappings;
    private List<EventImportSourceDto> eventImportSources;

    DbEventProcessPublishStateDtoBuilder() {}

    public DbEventProcessPublishStateDtoBuilder id(final String id) {
      this.id = id;
      return this;
    }

    public DbEventProcessPublishStateDtoBuilder processMappingId(final String processMappingId) {
      this.processMappingId = processMappingId;
      return this;
    }

    public DbEventProcessPublishStateDtoBuilder name(final String name) {
      this.name = name;
      return this;
    }

    public DbEventProcessPublishStateDtoBuilder publishDateTime(
        final OffsetDateTime publishDateTime) {
      this.publishDateTime = publishDateTime;
      return this;
    }

    public DbEventProcessPublishStateDtoBuilder state(final EventProcessState state) {
      this.state = state;
      return this;
    }

    public DbEventProcessPublishStateDtoBuilder publishProgress(final Double publishProgress) {
      this.publishProgress = publishProgress;
      return this;
    }

    public DbEventProcessPublishStateDtoBuilder deleted(final Boolean deleted) {
      deleted$value = deleted;
      deleted$set = true;
      return this;
    }

    public DbEventProcessPublishStateDtoBuilder xml(final String xml) {
      this.xml = xml;
      return this;
    }

    public DbEventProcessPublishStateDtoBuilder mappings(final List<DbEventMappingDto> mappings) {
      this.mappings = mappings;
      return this;
    }

    public DbEventProcessPublishStateDtoBuilder eventImportSources(
        final List<EventImportSourceDto> eventImportSources) {
      this.eventImportSources = eventImportSources;
      return this;
    }

    public DbEventProcessPublishStateDto build() {
      Boolean deleted$value = this.deleted$value;
      if (!deleted$set) {
        deleted$value = DbEventProcessPublishStateDto.$default$deleted();
      }
      return new DbEventProcessPublishStateDto(
          id,
          processMappingId,
          name,
          publishDateTime,
          state,
          publishProgress,
          deleted$value,
          xml,
          mappings,
          eventImportSources);
    }

    @Override
    public String toString() {
      return "DbEventProcessPublishStateDto.DbEventProcessPublishStateDtoBuilder(id="
          + id
          + ", processMappingId="
          + processMappingId
          + ", name="
          + name
          + ", publishDateTime="
          + publishDateTime
          + ", state="
          + state
          + ", publishProgress="
          + publishProgress
          + ", deleted$value="
          + deleted$value
          + ", xml="
          + xml
          + ", mappings="
          + mappings
          + ", eventImportSources="
          + eventImportSources
          + ")";
    }
  }
}
