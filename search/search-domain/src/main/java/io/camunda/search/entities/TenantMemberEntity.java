/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TenantMemberEntity(
    // Vestigial; not read by any consumer. The ES/OS primary handler does not persist this on
    // member child docs (tenantId only lives in the join parent routing), so reads from ES/OS
    // return null; RDBMS populates it from the FK column. Candidate for removal in a follow-up.
    @Nullable String tenantId, String id, EntityType entityType) {

  public TenantMemberEntity {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(entityType, "entityType");
  }
}
