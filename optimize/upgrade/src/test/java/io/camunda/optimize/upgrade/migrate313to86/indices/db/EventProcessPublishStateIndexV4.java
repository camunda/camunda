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
import io.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;

public abstract class EventProcessPublishStateIndexV4<TBuilder>
    extends DefaultIndexMappingCreator<TBuilder> {

  public static final int VERSION = 4;

  public static final String ID = "id";
  public static final String PROCESS_MAPPING_ID = "processMappingId";
  public static final String NAME = "name";
  public static final String PUBLISH_DATE_TIME = "publishDateTime";
  public static final String STATE = "state";
  public static final String PUBLISH_PROGRESS = "publishProgress";
  public static final String DELETED = "deleted";
  public static final String XML = "xml";
  public static final String MAPPINGS = "mappings";
  public static final String EVENT_IMPORT_SOURCES = "eventImportSources";

  public static final String FLOWNODE_ID = "flowNodeId";
  public static final String START = "start";
  public static final String END = "end";

  public static final String GROUP = "group";
  public static final String SOURCE = "source";
  public static final String EVENT_NAME = "eventName";
  public static final String EVENT_LABEL = "eventLabel";

  public static final String FIRST_EVENT_FOR_IMPORT_SOURCE_TIMESTAMP =
      "firstEventForSourceAtTimeOfPublishTimestamp";
  public static final String PUBLISH_COMPLETED_TIMESTAMP =
      "lastEventForSourceAtTimeOfPublishTimestamp";
  public static final String LAST_IMPORT_EXECUTION_TIMESTAMP = "lastImportExecutionTimestamp";
  public static final String LAST_IMPORTED_EVENT_TIMESTAMP = "lastImportedEventTimestamp";
  public static final String EVENT_IMPORT_SOURCE_TYPE = "eventImportSourceType";
  public static final String EVENT_IMPORT_SOURCE_CONFIGS = "eventSourceConfigurations";

  @Override
  public String getIndexName() {
    return "event-process-publish-state";
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public TypeMapping.Builder addProperties(final TypeMapping.Builder builder) {

    return builder
        .properties(ID, p -> p.keyword(k -> k))
        .properties(PROCESS_MAPPING_ID, p -> p.keyword(k -> k))
        .properties(NAME, p -> p.keyword(k -> k))
        .properties(PUBLISH_DATE_TIME, p -> p.date(k -> k.format(OPTIMIZE_DATE_FORMAT)))
        .properties(STATE, p -> p.keyword(k -> k))
        .properties(PUBLISH_PROGRESS, p -> p.double_(k -> k))
        .properties(DELETED, p -> p.boolean_(k -> k))
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
            EVENT_IMPORT_SOURCES,
            p ->
                p.object(
                    k ->
                        k.properties(
                                FIRST_EVENT_FOR_IMPORT_SOURCE_TIMESTAMP,
                                p2 -> p2.date(k2 -> k2.format(OPTIMIZE_DATE_FORMAT)))
                            .properties(
                                PUBLISH_COMPLETED_TIMESTAMP,
                                p2 -> p2.date(k2 -> k2.format(OPTIMIZE_DATE_FORMAT)))
                            .properties(
                                LAST_IMPORT_EXECUTION_TIMESTAMP,
                                p2 -> p2.date(k2 -> k2.format(OPTIMIZE_DATE_FORMAT)))
                            .properties(
                                LAST_IMPORTED_EVENT_TIMESTAMP,
                                p2 -> p2.date(k2 -> k2.format(OPTIMIZE_DATE_FORMAT)))
                            .properties(EVENT_IMPORT_SOURCE_TYPE, p2 -> p2.keyword(k2 -> k2))
                            .properties(
                                EVENT_IMPORT_SOURCE_CONFIGS,
                                p2 -> p2.object(k2 -> k2.dynamic(DynamicMapping.True)))));
  }
}
