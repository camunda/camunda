/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.ReportType;
import io.camunda.optimize.dto.optimize.RoleType;
import io.camunda.optimize.dto.optimize.query.collection.CollectionEntity;
import io.camunda.optimize.dto.optimize.query.entity.EntityResponseDto;
import io.camunda.optimize.dto.optimize.query.entity.EntityType;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;

public class ReportDefinitionDto<D extends ReportDataDto> implements CollectionEntity {

  protected String id;
  protected String name;
  protected String description;
  protected OffsetDateTime lastModified;
  protected OffsetDateTime created;
  protected String owner;
  protected String lastModifier;
  protected String collectionId;

  @Valid protected D data;

  private final boolean combined = false;

  private final ReportType reportType;

  protected ReportDefinitionDto(final D data, final Boolean combined, final ReportType reportType) {
    this.data = data;
    // this.combined = combined;
    this.reportType = reportType;
  }

  public ReportDefinitionDto(
      final String id,
      final String name,
      final String description,
      final OffsetDateTime lastModified,
      final OffsetDateTime created,
      final String owner,
      final String lastModifier,
      final String collectionId,
      @Valid final D data,
      final boolean combined,
      final ReportType reportType) {
    this.id = id;
    this.name = name;
    this.description = description;
    this.lastModified = lastModified;
    this.created = created;
    this.owner = owner;
    this.lastModifier = lastModifier;
    this.collectionId = collectionId;
    this.data = data;
    // this.combined = combined;
    this.reportType = reportType;
  }

  protected ReportDefinitionDto(final ReportDefinitionDtoBuilder<D, ?, ?> b) {
    id = b.id;
    name = b.name;
    description = b.description;
    lastModified = b.lastModified;
    created = b.created;
    owner = b.owner;
    lastModifier = b.lastModifier;
    collectionId = b.collectionId;
    data = b.data;
    // combined = b.combined;
    reportType = b.reportType;
  }

  @JsonIgnore
  public DefinitionType getDefinitionType() {
    return reportType.toDefinitionType();
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public String getCollectionId() {
    return collectionId;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getOwner() {
    return owner;
  }

  @Override
  public OffsetDateTime getLastModified() {
    return lastModified;
  }

  @Override
  public EntityResponseDto toEntityDto(final RoleType roleType) {
    return new EntityResponseDto(
        getId(),
        getName(),
        getDescription(),
        getLastModified(),
        getCreated(),
        getOwner(),
        getLastModifier(),
        EntityType.REPORT,
        combined,
        reportType,
        roleType);
  }

  public void setLastModified(final OffsetDateTime lastModified) {
    this.lastModified = lastModified;
  }

  public void setOwner(final String owner) {
    this.owner = owner;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public void setCollectionId(final String collectionId) {
    this.collectionId = collectionId;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(final String description) {
    this.description = description;
  }

  public OffsetDateTime getCreated() {
    return created;
  }

  public void setCreated(final OffsetDateTime created) {
    this.created = created;
  }

  public String getLastModifier() {
    return lastModifier;
  }

  public void setLastModifier(final String lastModifier) {
    this.lastModifier = lastModifier;
  }

  public @Valid D getData() {
    return data;
  }

  public void setData(@Valid final D data) {
    this.data = data;
  }

  public boolean isCombined() {
    return combined;
  }

  public ReportType getReportType() {
    return reportType;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ReportDefinitionDto;
  }

  @Override
  public int hashCode() {
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  public String toString() {
    return "ReportDefinitionDto(id="
        + getId()
        + ", name="
        + getName()
        + ", description="
        + getDescription()
        + ", lastModified="
        + getLastModified()
        + ", created="
        + getCreated()
        + ", owner="
        + getOwner()
        + ", lastModifier="
        + getLastModifier()
        + ", collectionId="
        + getCollectionId()
        + ", data="
        + getData()
        + ", combined="
        + isCombined()
        + ", reportType="
        + getReportType()
        + ")";
  }

  public static <D extends ReportDataDto> ReportDefinitionDtoBuilder<D, ?, ?> builder() {
    return new ReportDefinitionDtoBuilderImpl<D>();
  }

  public ReportDefinitionDtoBuilder<D, ?, ?> toBuilder() {
    return new ReportDefinitionDtoBuilderImpl<D>().fillValuesFrom(this);
  }

  @SuppressWarnings("checkstyle:ConstantName")
  public static final class Fields {

    public static final String id = "id";
    public static final String name = "name";
    public static final String description = "description";
    public static final String lastModified = "lastModified";
    public static final String created = "created";
    public static final String owner = "owner";
    public static final String lastModifier = "lastModifier";
    public static final String collectionId = "collectionId";
    public static final String data = "data";
    public static final String combined = "combined";
    public static final String reportType = "reportType";
  }

  public abstract static class ReportDefinitionDtoBuilder<
      D extends ReportDataDto,
      C extends ReportDefinitionDto<D>,
      B extends ReportDefinitionDtoBuilder<D, C, B>> {

    private String id;
    private String name;
    private String description;
    private OffsetDateTime lastModified;
    private OffsetDateTime created;
    private String owner;
    private String lastModifier;
    private String collectionId;
    private @Valid D data;
    private boolean combined;
    private ReportType reportType;

    public @Valid D getData() {
      return data;
    }

    public B id(final String id) {
      this.id = id;
      return self();
    }

    public B name(final String name) {
      this.name = name;
      return self();
    }

    public B description(final String description) {
      this.description = description;
      return self();
    }

    public B lastModified(final OffsetDateTime lastModified) {
      this.lastModified = lastModified;
      return self();
    }

    public B created(final OffsetDateTime created) {
      this.created = created;
      return self();
    }

    public B owner(final String owner) {
      this.owner = owner;
      return self();
    }

    public B lastModifier(final String lastModifier) {
      this.lastModifier = lastModifier;
      return self();
    }

    public B collectionId(final String collectionId) {
      this.collectionId = collectionId;
      return self();
    }

    public B data(@Valid final D data) {
      this.data = data;
      return self();
    }

    public B combined(final boolean combined) {
      this.combined = combined;
      return self();
    }

    public B reportType(final ReportType reportType) {
      this.reportType = reportType;
      return self();
    }

    private static <D extends ReportDataDto> void fillValuesFromInstanceIntoBuilder(
        final ReportDefinitionDto<D> instance, final ReportDefinitionDtoBuilder<D, ?, ?> b) {
      b.id(instance.id);
      b.name(instance.name);
      b.description(instance.description);
      b.lastModified(instance.lastModified);
      b.created(instance.created);
      b.owner(instance.owner);
      b.lastModifier(instance.lastModifier);
      b.collectionId(instance.collectionId);
      b.data(instance.data);
      b.combined(instance.combined);
      b.reportType(instance.reportType);
    }

    protected B fillValuesFrom(final C instance) {
      ReportDefinitionDtoBuilder.fillValuesFromInstanceIntoBuilder(instance, this);
      return self();
    }

    protected abstract B self();

    public abstract C build();

    @Override
    public String toString() {
      return "ReportDefinitionDto.ReportDefinitionDtoBuilder(id="
          + id
          + ", name="
          + name
          + ", description="
          + description
          + ", lastModified="
          + lastModified
          + ", created="
          + created
          + ", owner="
          + owner
          + ", lastModifier="
          + lastModifier
          + ", collectionId="
          + collectionId
          + ", data="
          + data
          + ", combined="
          + combined
          + ", reportType="
          + reportType
          + ")";
    }
  }

  private static final class ReportDefinitionDtoBuilderImpl<D extends ReportDataDto>
      extends ReportDefinitionDtoBuilder<
          D, ReportDefinitionDto<D>, ReportDefinitionDtoBuilderImpl<D>> {

    private ReportDefinitionDtoBuilderImpl() {}

    @Override
    protected ReportDefinitionDtoBuilderImpl<D> self() {
      return this;
    }

    @Override
    public ReportDefinitionDto<D> build() {
      return new ReportDefinitionDto<D>(this);
    }
  }
}
