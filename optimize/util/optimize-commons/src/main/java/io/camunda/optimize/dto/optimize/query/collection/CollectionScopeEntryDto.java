/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.collection;

import static io.camunda.optimize.dto.optimize.query.collection.ScopeComplianceType.COMPLIANT;
import static io.camunda.optimize.dto.optimize.query.collection.ScopeComplianceType.NON_DEFINITION_COMPLIANT;
import static io.camunda.optimize.dto.optimize.query.collection.ScopeComplianceType.NON_TENANT_COMPLIANT;

import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.ReportConstants;
import java.util.List;
import java.util.Optional;

public class CollectionScopeEntryDto {

  private static final String ID_SEGMENT_SEPARATOR = ":";

  private String id;
  private DefinitionType definitionType;
  private String definitionKey;
  private List<String> tenants = ReportConstants.DEFAULT_TENANT_IDS;

  public CollectionScopeEntryDto(final String id) {
    this(
        DefinitionType.valueOf(id.split(ID_SEGMENT_SEPARATOR)[0].toUpperCase()),
        id.split(ID_SEGMENT_SEPARATOR)[1]);
  }

  public CollectionScopeEntryDto(final CollectionScopeEntryDto oldEntry) {
    definitionKey = oldEntry.definitionKey;
    definitionType = oldEntry.definitionType;
    tenants = oldEntry.tenants;
    id = convertTypeAndKeyToScopeEntryId(definitionType, definitionKey);
  }

  public CollectionScopeEntryDto(final DefinitionType definitionType, final String definitionKey) {
    this(definitionType, definitionKey, ReportConstants.DEFAULT_TENANT_IDS);
  }

  public CollectionScopeEntryDto(
      final DefinitionType definitionType, final String definitionKey, final List<String> tenants) {
    id = convertTypeAndKeyToScopeEntryId(definitionType, definitionKey);
    this.definitionType = definitionType;
    this.definitionKey = definitionKey;
    this.tenants = tenants;
  }

  protected CollectionScopeEntryDto() {}

  public String getId() {
    return Optional.ofNullable(id)
        .orElse(convertTypeAndKeyToScopeEntryId(definitionType, definitionKey));
  }

  protected void setId(final String id) {
    this.id = id;
  }

  public ScopeComplianceType getComplianceType(
      final DefinitionType definitionType, final String definitionKey, final List<String> tenants) {
    if (!isInDefinitionScope(definitionType, definitionKey)) {
      return NON_DEFINITION_COMPLIANT;
    } else if (!isInTenantScope(tenants)) {
      return NON_TENANT_COMPLIANT;
    }
    return COMPLIANT;
  }

  private boolean isInDefinitionScope(
      final DefinitionType definitionType, final String definitionKey) {
    return this.definitionType.equals(definitionType) && this.definitionKey.equals(definitionKey);
  }

  private boolean isInTenantScope(final List<String> givenTenants) {
    return givenTenants != null && tenants.containsAll(givenTenants);
  }

  public static String convertTypeAndKeyToScopeEntryId(
      final DefinitionType definitionType, final String definitionKey) {
    return definitionType.getId() + ID_SEGMENT_SEPARATOR + definitionKey;
  }

  public DefinitionType getDefinitionType() {
    return definitionType;
  }

  public void setDefinitionType(final DefinitionType definitionType) {
    this.definitionType = definitionType;
  }

  public String getDefinitionKey() {
    return definitionKey;
  }

  public void setDefinitionKey(final String definitionKey) {
    this.definitionKey = definitionKey;
  }

  public List<String> getTenants() {
    return tenants;
  }

  public void setTenants(final List<String> tenants) {
    this.tenants = tenants;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof CollectionScopeEntryDto;
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
    if (!(o instanceof CollectionScopeEntryDto)) {
      return false;
    }
    final CollectionScopeEntryDto other = (CollectionScopeEntryDto) o;
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
    return "CollectionScopeEntryDto(id="
        + getId()
        + ", definitionType="
        + getDefinitionType()
        + ", definitionKey="
        + getDefinitionKey()
        + ", tenants="
        + getTenants()
        + ")";
  }

  public enum Fields {
    id,
    definitionType,
    definitionKey,
    tenants
  }
}
