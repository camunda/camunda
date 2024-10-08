/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.migrate313to86.indices.db;

import static io.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;

import co.elastic.clients.elasticsearch._types.mapping.DynamicMapping;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import io.camunda.optimize.dto.optimize.IdentityDto;
import io.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;

public abstract class EventProcessMappingIndexV4<TBuilder>
    extends DefaultIndexMappingCreator<TBuilder> {

  public static final int VERSION = 4;

  public static final String ID = "id";
  public static final String NAME = "name";
  public static final String XML = "xml";
  public static final String LAST_MODIFIED = "lastModified";
  public static final String LAST_MODIFIER = "lastModifier";
  public static final String MAPPINGS = "mappings";
  public static final String EVENT_SOURCES = "eventSources";

  public static final String FLOWNODE_ID = "flowNodeId";
  public static final String START = "start";
  public static final String END = "end";

  public static final String GROUP = "group";
  public static final String SOURCE = "source";
  public static final String EVENT_NAME = "eventName";
  public static final String EVENT_LABEL = "eventLabel";

  public static final String EVENT_SOURCE_ID = "id";
  public static final String EVENT_SOURCE_TYPE = "type";
  public static final String EVENT_SOURCE_CONFIG = "configuration";

  public static final String ROLES = "roles";
  public static final String ROLE_ID = "id";
  public static final String ROLE_IDENTITY = "identity";

  public static final String ROLE_IDENTITY_ID = IdentityDto.Fields.id;
  public static final String ROLE_IDENTITY_TYPE = IdentityDto.Fields.type;

  @Override
  public String getIndexName() {
    return "event-process-mapping";
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public TypeMapping.Builder addProperties(final TypeMapping.Builder builder) {

    return builder
        .properties(ID, p -> p.keyword(k -> k))
        .properties(NAME, p -> p.keyword(k -> k))
        .properties(LAST_MODIFIER, p -> p.keyword(k -> k))
        .properties(LAST_MODIFIED, p -> p.date(k -> k.format(OPTIMIZE_DATE_FORMAT)))
        .properties(XML, p -> p.text(k -> k.index(true).analyzer("is_present_analyzer")))
        .properties(
            MAPPINGS,
            p ->
                p.object(
                    k ->
                        k.properties(FLOWNODE_ID, p2 -> p2.keyword(k2 -> k2))
                            .properties(
                                START,
                                p2 ->
                                    p2.object(
                                        k2 ->
                                            k2.properties(GROUP, p3 -> p3.keyword(k3 -> k3))
                                                .properties(SOURCE, p3 -> p3.keyword(k3 -> k3))
                                                .properties(EVENT_NAME, p3 -> p3.keyword(k3 -> k3))
                                                .properties(
                                                    EVENT_LABEL, p3 -> p3.keyword(k3 -> k3))))
                            .properties(
                                END,
                                p2 ->
                                    p2.object(
                                        k2 ->
                                            k2.properties(GROUP, p3 -> p3.keyword(k3 -> k3))
                                                .properties(SOURCE, p3 -> p3.keyword(k3 -> k3))
                                                .properties(EVENT_NAME, p3 -> p3.keyword(k3 -> k3))
                                                .properties(
                                                    EVENT_LABEL, p3 -> p3.keyword(k3 -> k3))))))
        .properties(
            EVENT_SOURCES,
            p ->
                p.object(
                    k ->
                        k.dynamic(DynamicMapping.True)
                            .properties(EVENT_SOURCE_ID, p2 -> p2.keyword(k2 -> k2))
                            .properties(EVENT_SOURCE_TYPE, p2 -> p2.keyword(k2 -> k2))
                            .properties(
                                EVENT_SOURCE_CONFIG,
                                p2 -> p2.object(k2 -> k2.dynamic(DynamicMapping.True)))))
        .properties(
            ROLES,
            p ->
                p.object(
                    k ->
                        k.properties(ROLE_ID, p2 -> p2.keyword(k2 -> k2))
                            .properties(
                                ROLE_IDENTITY,
                                p2 ->
                                    p2.object(
                                        k2 ->
                                            k2.properties(
                                                    ROLE_IDENTITY_ID, p3 -> p3.keyword(k3 -> k3))
                                                .properties(
                                                    ROLE_IDENTITY_TYPE,
                                                    p3 -> p3.keyword(k3 -> k3))))));
  }
}
