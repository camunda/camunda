/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Objects;

/**
 * Represents an entity that can be written to ElasticSearch or OpenSearch
 *
 * @param <T>
 */
public abstract class AbstractExporterEntity<T extends AbstractExporterEntity<T>>
    implements ExporterEntity<T> {

  /** Deprecated: Use TenantOwned.DEFAULT_TENANT_IDENTIFIER instead. */
  @Deprecated public static final String DEFAULT_TENANT_ID = "<default>";

  private String id;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Long key;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Integer partitionId;

  @Override
  public String getId() {
    return id;
  }

  @Override
  public T setId(final String id) {
    this.id = id;
    return (T) this;
  }

  public Long getKey() {
    return key;
  }

  public T setKey(final Long key) {
    this.key = key;
    return (T) this;
  }

  public Integer getPartitionId() {
    return partitionId;
  }

  public T setPartitionId(final Integer partitionId) {
    this.partitionId = partitionId;
    return (T) this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, key, partitionId);
  }

  @Override
  public boolean equals(final Object o) {
    if (!(o instanceof final AbstractExporterEntity<?> that)) {
      return false;
    }
    return Objects.equals(id, that.id)
        && Objects.equals(key, that.key)
        && Objects.equals(partitionId, that.partitionId);
  }
}
