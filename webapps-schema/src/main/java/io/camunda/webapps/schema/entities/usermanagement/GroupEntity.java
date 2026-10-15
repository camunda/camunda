/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.usermanagement;

import io.camunda.webapps.schema.entities.AbstractExporterEntity;
import io.camunda.webapps.schema.entities.BeforeVersion880;
import io.camunda.webapps.schema.entities.SinceVersion;
import java.time.OffsetDateTime;

public class GroupEntity extends AbstractExporterEntity<GroupEntity> {

  @BeforeVersion880 private Long key;
  @BeforeVersion880 private String groupId;
  @BeforeVersion880 private String name;
  @BeforeVersion880 private String description;

  @SinceVersion(value = "8.11.0", requireDefault = false)
  private String externalId;

  @SinceVersion(value = "8.11.0", requireDefault = false)
  private String source;

  @SinceVersion(value = "8.11.0", requireDefault = false)
  private String attributes;

  @SinceVersion(value = "8.11.0", requireDefault = false)
  private OffsetDateTime createdAt;

  @SinceVersion(value = "8.11.0", requireDefault = false)
  private OffsetDateTime lastModifiedAt;

  @BeforeVersion880 private EntityJoinRelation join;

  public Long getKey() {
    return key;
  }

  public GroupEntity setKey(final Long key) {
    this.key = key;
    return this;
  }

  public String getGroupId() {
    return groupId;
  }

  public GroupEntity setGroupId(final String groupId) {
    this.groupId = groupId;
    return this;
  }

  public String getName() {
    return name;
  }

  public GroupEntity setName(final String name) {
    this.name = name;
    return this;
  }

  public String getDescription() {
    return description;
  }

  public GroupEntity setDescription(final String description) {
    this.description = description;
    return this;
  }

  public String getSource() {
    return source;
  }

  public GroupEntity setSource(final String source) {
    this.source = source;
    return this;
  }

  public String getExternalId() {
    return externalId;
  }

  public GroupEntity setExternalId(final String externalId) {
    this.externalId = externalId;
    return this;
  }

  /** JSON object serialized as a string; not indexed. */
  public String getAttributes() {
    return attributes;
  }

  public GroupEntity setAttributes(final String attributes) {
    this.attributes = attributes;
    return this;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public GroupEntity setCreatedAt(final OffsetDateTime createdAt) {
    this.createdAt = createdAt;
    return this;
  }

  public OffsetDateTime getLastModifiedAt() {
    return lastModifiedAt;
  }

  public GroupEntity setLastModifiedAt(final OffsetDateTime lastModifiedAt) {
    this.lastModifiedAt = lastModifiedAt;
    return this;
  }

  public EntityJoinRelation getJoin() {
    return join;
  }

  public GroupEntity setJoin(final EntityJoinRelation join) {
    this.join = join;
    return this;
  }
}
