/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.schema.index;

import static io.camunda.optimize.service.db.DatabaseConstants.COLLECTION_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.MAPPING_ENABLED_SETTING;
import static io.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;

import io.camunda.optimize.dto.optimize.IdentityDto;
import io.camunda.optimize.dto.optimize.query.collection.BaseCollectionDefinitionDto;
import io.camunda.optimize.dto.optimize.query.collection.CollectionDataDto;
import io.camunda.optimize.dto.optimize.query.collection.CollectionRoleRequestDto;
import io.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryDto;
import io.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;
import java.io.IOException;
import org.elasticsearch.xcontent.XContentBuilder;

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
  public XContentBuilder addProperties(XContentBuilder xContentBuilder) throws IOException {
    // @formatter:off
    XContentBuilder newBuilder =
        xContentBuilder
            .startObject(ID)
            .field("type", "keyword")
            .endObject()
            .startObject(BaseCollectionDefinitionDto.Fields.name.name())
            .field("type", "keyword")
            .endObject()
            .startObject(BaseCollectionDefinitionDto.Fields.lastModified.name())
            .field("type", "date")
            .field("format", OPTIMIZE_DATE_FORMAT)
            .endObject()
            .startObject(BaseCollectionDefinitionDto.Fields.created.name())
            .field("type", "date")
            .field("format", OPTIMIZE_DATE_FORMAT)
            .endObject()
            .startObject(BaseCollectionDefinitionDto.Fields.owner.name())
            .field("type", "keyword")
            .endObject()
            .startObject(BaseCollectionDefinitionDto.Fields.lastModifier.name())
            .field("type", "keyword")
            .endObject()
            .startObject(BaseCollectionDefinitionDto.Fields.automaticallyCreated.name())
            .field("type", "boolean")
            .endObject();
    newBuilder = addDataField(newBuilder);
    return newBuilder;
    // @formatter:on
  }

  private XContentBuilder addDataField(XContentBuilder xContentBuilder) throws IOException {
    // @formatter:off
    final XContentBuilder contentBuilder =
        xContentBuilder
            .startObject(DATA)
            .field("type", "nested")
            .startObject("properties")
            .startObject(CollectionDataDto.Fields.configuration.name())
            .field(MAPPING_ENABLED_SETTING, false)
            .endObject();
    addRolesField(contentBuilder);
    addScopeField(contentBuilder).endObject().endObject();
    // @formatter:on
    return contentBuilder;
  }

  private XContentBuilder addScopeField(final XContentBuilder xContentBuilder) throws IOException {
    // @formatter:off
    return xContentBuilder
        .startObject(SCOPE)
        .field("type", "nested")
        .startObject("properties")
        .startObject(CollectionScopeEntryDto.Fields.definitionKey.name())
        .field("type", "keyword")
        .endObject()
        .startObject(CollectionScopeEntryDto.Fields.definitionType.name())
        .field("type", "keyword")
        .endObject()
        .startObject(CollectionScopeEntryDto.Fields.id.name())
        .field("type", "keyword")
        .endObject()
        .startObject(CollectionScopeEntryDto.Fields.tenants.name())
        .field("type", "keyword")
        .endObject()
        .endObject()
        .endObject();

    // @formatter:on
  }

  private XContentBuilder addRolesField(final XContentBuilder xContentBuilder) throws IOException {
    // @formatter:off
    return xContentBuilder
        .startObject(CollectionDataDto.Fields.roles.name())
        .field("type", "nested")
        .startObject("properties")
        .startObject(CollectionRoleRequestDto.Fields.id.name())
        .field("type", "keyword")
        .endObject()
        .startObject(CollectionRoleRequestDto.Fields.identity.name())
        .field("type", "object")
        .startObject("properties")
        .startObject(IdentityDto.Fields.id)
        .field("type", "keyword")
        .endObject()
        .startObject(IdentityDto.Fields.type)
        .field("type", "keyword")
        .endObject()
        .endObject()
        .endObject()
        .startObject(CollectionRoleRequestDto.Fields.role.name())
        .field("type", "keyword")
        .endObject()
        .endObject()
        .endObject();
    // @formatter:on
  }
}
