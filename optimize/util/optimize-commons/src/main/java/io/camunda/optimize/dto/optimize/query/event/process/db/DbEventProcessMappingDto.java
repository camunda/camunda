/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.event.process.db;

import io.camunda.optimize.dto.optimize.IdentityDto;
import io.camunda.optimize.dto.optimize.OptimizeDto;
import io.camunda.optimize.dto.optimize.query.event.process.EventMappingDto;
import io.camunda.optimize.dto.optimize.query.event.process.EventProcessMappingDto;
import io.camunda.optimize.dto.optimize.query.event.process.EventProcessRoleRequestDto;
import io.camunda.optimize.dto.optimize.query.event.process.source.EventSourceEntryDto;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class DbEventProcessMappingDto implements OptimizeDto {

  private String id;
  private String name;
  private String xml;
  private OffsetDateTime lastModified;
  private String lastModifier;
  private List<DbEventMappingDto> mappings;
  private List<EventSourceEntryDto<?>> eventSources;
  private List<EventProcessRoleRequestDto<IdentityDto>> roles;

  public DbEventProcessMappingDto(
      final String id,
      final String name,
      final String xml,
      final OffsetDateTime lastModified,
      final String lastModifier,
      final List<DbEventMappingDto> mappings,
      final List<EventSourceEntryDto<?>> eventSources,
      final List<EventProcessRoleRequestDto<IdentityDto>> roles) {
    this.id = id;
    this.name = name;
    this.xml = xml;
    this.lastModified = lastModified;
    this.lastModifier = lastModifier;
    this.mappings = mappings;
    this.eventSources = eventSources;
    this.roles = roles;
  }

  public DbEventProcessMappingDto() {}

  public static DbEventProcessMappingDto fromEventProcessMappingDto(
      final EventProcessMappingDto eventMappingDto) {
    return DbEventProcessMappingDto.builder()
        .id(eventMappingDto.getId())
        .name(eventMappingDto.getName())
        .xml(eventMappingDto.getXml())
        .lastModified(eventMappingDto.getLastModified())
        .lastModifier(eventMappingDto.getLastModifier())
        .mappings(
            Optional.ofNullable(eventMappingDto.getMappings())
                .map(
                    mappings ->
                        mappings.keySet().stream()
                            .map(
                                flowNodeId ->
                                    DbEventMappingDto.fromEventMappingDto(
                                        flowNodeId, eventMappingDto.getMappings().get(flowNodeId)))
                            .collect(Collectors.toList()))
                .orElse(null))
        .eventSources(eventMappingDto.getEventSources())
        .roles(eventMappingDto.getRoles())
        .build();
  }

  public EventProcessMappingDto toEventProcessMappingDto() {
    return EventProcessMappingDto.builder()
        .id(id)
        .name(name)
        .xml(xml)
        .lastModified(lastModified)
        .lastModifier(lastModifier)
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
        .eventSources(eventSources)
        .roles(roles)
        .build();
  }

  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public String getXml() {
    return xml;
  }

  public void setXml(final String xml) {
    this.xml = xml;
  }

  public OffsetDateTime getLastModified() {
    return lastModified;
  }

  public void setLastModified(final OffsetDateTime lastModified) {
    this.lastModified = lastModified;
  }

  public String getLastModifier() {
    return lastModifier;
  }

  public void setLastModifier(final String lastModifier) {
    this.lastModifier = lastModifier;
  }

  public List<DbEventMappingDto> getMappings() {
    return mappings;
  }

  public void setMappings(final List<DbEventMappingDto> mappings) {
    this.mappings = mappings;
  }

  public List<EventSourceEntryDto<?>> getEventSources() {
    return eventSources;
  }

  public void setEventSources(final List<EventSourceEntryDto<?>> eventSources) {
    this.eventSources = eventSources;
  }

  public List<EventProcessRoleRequestDto<IdentityDto>> getRoles() {
    return roles;
  }

  public void setRoles(final List<EventProcessRoleRequestDto<IdentityDto>> roles) {
    this.roles = roles;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof DbEventProcessMappingDto;
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
    if (!(o instanceof DbEventProcessMappingDto)) {
      return false;
    }
    final DbEventProcessMappingDto other = (DbEventProcessMappingDto) o;
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
    return "DbEventProcessMappingDto(id="
        + getId()
        + ", name="
        + getName()
        + ", xml="
        + getXml()
        + ", lastModified="
        + getLastModified()
        + ", lastModifier="
        + getLastModifier()
        + ", mappings="
        + getMappings()
        + ", eventSources="
        + getEventSources()
        + ", roles="
        + getRoles()
        + ")";
  }

  public static DbEventProcessMappingDtoBuilder builder() {
    return new DbEventProcessMappingDtoBuilder();
  }

  public static final class Fields {

    public static final String id = "id";
    public static final String name = "name";
    public static final String xml = "xml";
    public static final String lastModified = "lastModified";
    public static final String lastModifier = "lastModifier";
    public static final String mappings = "mappings";
    public static final String eventSources = "eventSources";
    public static final String roles = "roles";
  }

  public static class DbEventProcessMappingDtoBuilder {

    private String id;
    private String name;
    private String xml;
    private OffsetDateTime lastModified;
    private String lastModifier;
    private List<DbEventMappingDto> mappings;
    private List<EventSourceEntryDto<?>> eventSources;
    private List<EventProcessRoleRequestDto<IdentityDto>> roles;

    DbEventProcessMappingDtoBuilder() {}

    public DbEventProcessMappingDtoBuilder id(final String id) {
      this.id = id;
      return this;
    }

    public DbEventProcessMappingDtoBuilder name(final String name) {
      this.name = name;
      return this;
    }

    public DbEventProcessMappingDtoBuilder xml(final String xml) {
      this.xml = xml;
      return this;
    }

    public DbEventProcessMappingDtoBuilder lastModified(final OffsetDateTime lastModified) {
      this.lastModified = lastModified;
      return this;
    }

    public DbEventProcessMappingDtoBuilder lastModifier(final String lastModifier) {
      this.lastModifier = lastModifier;
      return this;
    }

    public DbEventProcessMappingDtoBuilder mappings(final List<DbEventMappingDto> mappings) {
      this.mappings = mappings;
      return this;
    }

    public DbEventProcessMappingDtoBuilder eventSources(
        final List<EventSourceEntryDto<?>> eventSources) {
      this.eventSources = eventSources;
      return this;
    }

    public DbEventProcessMappingDtoBuilder roles(
        final List<EventProcessRoleRequestDto<IdentityDto>> roles) {
      this.roles = roles;
      return this;
    }

    public DbEventProcessMappingDto build() {
      return new DbEventProcessMappingDto(
          id, name, xml, lastModified, lastModifier, mappings, eventSources, roles);
    }

    @Override
    public String toString() {
      return "DbEventProcessMappingDto.DbEventProcessMappingDtoBuilder(id="
          + id
          + ", name="
          + name
          + ", xml="
          + xml
          + ", lastModified="
          + lastModified
          + ", lastModifier="
          + lastModifier
          + ", mappings="
          + mappings
          + ", eventSources="
          + eventSources
          + ", roles="
          + roles
          + ")";
    }
  }
}
