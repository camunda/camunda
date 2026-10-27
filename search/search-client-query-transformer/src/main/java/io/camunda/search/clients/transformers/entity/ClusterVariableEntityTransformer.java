/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.entity;

import io.camunda.search.clients.transformers.ServiceTransformer;
import io.camunda.search.entities.ClusterVariableEntity;
import io.camunda.search.entities.ClusterVariableEntity.MetadataEntry;
import io.camunda.search.entities.ClusterVariableKind;
import io.camunda.search.entities.ClusterVariableScope;
import java.util.List;

public class ClusterVariableEntityTransformer
    implements ServiceTransformer<
        io.camunda.webapps.schema.entities.clustervariable.ClusterVariableEntity,
        ClusterVariableEntity> {

  @Override
  public ClusterVariableEntity apply(
      final io.camunda.webapps.schema.entities.clustervariable.ClusterVariableEntity value) {
    final var webappsKind = value.getKind();
    final ClusterVariableKind kind =
        webappsKind != null
            ? ClusterVariableKind.valueOf(webappsKind.name())
            : ClusterVariableKind.JSON;
    return new ClusterVariableEntity(
        value.getId(),
        value.getName(),
        value.getValue(),
        value.getFullValue(),
        value.getIsPreview(),
        ClusterVariableScope.valueOf(value.getScope().name()),
        value.getTenantId(),
        toMetadata(value.getMetadata()),
        kind);
  }

  private List<MetadataEntry> toMetadata(
      final List<
              io.camunda.webapps.schema.entities.clustervariable.ClusterVariableEntity
                  .MetadataEntry>
          metadata) {
    if (metadata == null) {
      return null;
    }
    return metadata.stream()
        .map(m -> new MetadataEntry(m.key(), m.value(), m.valueNumber()))
        .toList();
  }
}
