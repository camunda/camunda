/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.schema.index;

import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.query.collection.BaseCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionDataDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleRequestDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryDto;
import org.camunda.optimize.service.es.schema.DefaultIndexMappingCreator;
import org.elasticsearch.xcontent.XContentBuilder;

import java.io.IOException;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.COLLECTION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.MAPPING_ENABLED_SETTING;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;

public class CollectionIndex extends DefaultIndexMappingCreator {

  public static final int VERSION = 4;

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
    XContentBuilder newBuilder = xContentBuilder
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
    .endObject();
    newBuilder = addDataField(newBuilder);
    return newBuilder;
     // @formatter:on
  }

  private XContentBuilder addDataField(XContentBuilder xContentBuilder) throws IOException {
    // @formatter:off
    final XContentBuilder contentBuilder = xContentBuilder.
      startObject(DATA)
        .field("type", "nested")
        .startObject("properties")
          .startObject(CollectionDataDto.Fields.configuration.name())
            .field(MAPPING_ENABLED_SETTING, false)
          .endObject();
          addRolesField(contentBuilder);
          addScopeField(contentBuilder)
        .endObject()
      .endObject();
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
    return xContentBuilder.
      startObject(CollectionDataDto.Fields.roles.name())
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
