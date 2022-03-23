/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.collection;

import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.ReportConstants;

import java.util.List;
import java.util.Optional;

import static org.camunda.optimize.dto.optimize.query.collection.ScopeComplianceType.COMPLIANT;
import static org.camunda.optimize.dto.optimize.query.collection.ScopeComplianceType.NON_DEFINITION_COMPLIANT;
import static org.camunda.optimize.dto.optimize.query.collection.ScopeComplianceType.NON_TENANT_COMPLIANT;

@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@FieldNameConstants(asEnum = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class CollectionScopeEntryDto {
  private static final String ID_SEGMENT_SEPARATOR = ":";

  @EqualsAndHashCode.Include
  @Setter(value = AccessLevel.PROTECTED)
  private String id;
  private DefinitionType definitionType;
  private String definitionKey;
  private List<String> tenants = ReportConstants.DEFAULT_TENANT_IDS;

  public CollectionScopeEntryDto(final String id) {
    this(DefinitionType.valueOf(id.split(ID_SEGMENT_SEPARATOR)[0].toUpperCase()), id.split(ID_SEGMENT_SEPARATOR)[1]);
  }

  public CollectionScopeEntryDto(CollectionScopeEntryDto oldEntry) {
    this.definitionKey = oldEntry.definitionKey;
    this.definitionType = oldEntry.definitionType;
    this.tenants = oldEntry.tenants;
    this.id = convertTypeAndKeyToScopeEntryId(this.definitionType, this.definitionKey);
  }

  public CollectionScopeEntryDto(final DefinitionType definitionType, final String definitionKey) {
    this(definitionType, definitionKey, ReportConstants.DEFAULT_TENANT_IDS);
  }

  public CollectionScopeEntryDto(final DefinitionType definitionType,
                                 final String definitionKey,
                                 final List<String> tenants) {
    this.id = convertTypeAndKeyToScopeEntryId(definitionType, definitionKey);
    this.definitionType = definitionType;
    this.definitionKey = definitionKey;
    this.tenants = tenants;
  }

  public String getId() {
    return Optional.ofNullable(id).orElse(convertTypeAndKeyToScopeEntryId(definitionType, definitionKey));
  }

  public ScopeComplianceType getComplianceType(final DefinitionType definitionType,
                                               final String definitionKey,
                                               final List<String> tenants) {
    if (!isInDefinitionScope(definitionType, definitionKey)) {
      return NON_DEFINITION_COMPLIANT;
    } else if (!isInTenantScope(tenants)) {
      return NON_TENANT_COMPLIANT;
    }
    return COMPLIANT;
  }

  private boolean isInDefinitionScope(final DefinitionType definitionType,
                                      final String definitionKey) {
    return this.definitionType.equals(definitionType) &&
      this.definitionKey.equals(definitionKey);
  }

  private boolean isInTenantScope(final List<String> givenTenants) {
    return givenTenants != null && this.tenants.containsAll(givenTenants);
  }

  public static String convertTypeAndKeyToScopeEntryId(final DefinitionType definitionType,
                                                       final String definitionKey) {
    return definitionType.getId() + ID_SEGMENT_SEPARATOR + definitionKey;
  }
}

