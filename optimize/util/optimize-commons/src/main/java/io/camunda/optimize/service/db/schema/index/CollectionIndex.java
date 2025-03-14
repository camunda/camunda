/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.schema.index;

import static io.camunda.optimize.service.db.DatabaseConstants.COLLECTION_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;

import co.elastic.clients.elasticsearch._types.mapping.NestedProperty;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import io.camunda.optimize.dto.optimize.IdentityDto;
import io.camunda.optimize.dto.optimize.query.collection.BaseCollectionDefinitionDto;
import io.camunda.optimize.dto.optimize.query.collection.CollectionDataDto;
import io.camunda.optimize.dto.optimize.query.collection.CollectionRoleRequestDto;
import io.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryDto;
import io.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;

public abstract class CollectionIndex<TBuilder> extends DefaultIndexMappingCreator<TBuilder> {

  public static final int VERSION = 5;

  public static final String ID = BaseCollectionDefinitionDto.Fields.id.name();
  public static final String DATA = BaseCollectionDefinitionDto.Fields.data.name();
  public static final String SCOPE = CollectionDataDto.Fields.scope.name();

  @Override
  public String getIndexName() {
    return COLLECTION_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public TypeMapping.Builder addProperties(final TypeMapping.Builder builder) {
    return addDataField(
        builder
            .properties(ID, p -> p.keyword(k -> k))
            .properties(BaseCollectionDefinitionDto.Fields.name.name(), p -> p.keyword(k -> k))
            .properties(
                BaseCollectionDefinitionDto.Fields.lastModified.name(),
                p -> p.date(k -> k.format(OPTIMIZE_DATE_FORMAT)))
            .properties(
                BaseCollectionDefinitionDto.Fields.created.name(),
                p -> p.date(k -> k.format(OPTIMIZE_DATE_FORMAT)))
            .properties(BaseCollectionDefinitionDto.Fields.owner.name(), p -> p.keyword(k -> k))
            .properties(
                BaseCollectionDefinitionDto.Fields.lastModifier.name(), p -> p.keyword(k -> k))
            .properties(
                BaseCollectionDefinitionDto.Fields.automaticallyCreated.name(),
                p -> p.boolean_(k -> k)));
  }

  private TypeMapping.Builder addDataField(final TypeMapping.Builder xContentBuilder) {
    return xContentBuilder.properties(
        DATA,
        p ->
            p.nested(
                n ->
                    addScopeField(
                        addRolesField(
                            n.properties(
                                CollectionDataDto.Fields.configuration.name(),
                                m -> m.object(o -> o.enabled(false)))))));
  }

  private NestedProperty.Builder addScopeField(final NestedProperty.Builder builder) {
    return builder.properties(
        SCOPE,
        p ->
            p.nested(
                n ->
                    n.properties(
                            CollectionScopeEntryDto.Fields.definitionKey.name(),
                            np -> np.keyword(k -> k))
                        .properties(
                            CollectionScopeEntryDto.Fields.definitionType.name(),
                            np -> np.keyword(k -> k))
                        .properties(
                            CollectionScopeEntryDto.Fields.id.name(), np -> np.keyword(k -> k))
                        .properties(
                            CollectionScopeEntryDto.Fields.tenants.name(),
                            np -> np.keyword(k -> k))));
  }

  private NestedProperty.Builder addRolesField(final NestedProperty.Builder builder) {
    return builder.properties(
        CollectionDataDto.Fields.roles.name(),
        p ->
            p.nested(
                n ->
                    n.properties(
                            CollectionRoleRequestDto.Fields.id.name(), np -> np.keyword(k -> k))
                        .properties(
                            CollectionRoleRequestDto.Fields.identity.name(),
                            np ->
                                np.object(
                                    k ->
                                        k.properties(
                                                IdentityDto.Fields.id, ip -> ip.keyword(r -> r))
                                            .properties(
                                                IdentityDto.Fields.type, ip -> ip.keyword(r -> r))))
                        .properties(
                            CollectionRoleRequestDto.Fields.role.name(),
                            np -> np.keyword(k -> k))));
  }
}
