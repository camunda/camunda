/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.migrate313to86.indices;

import static io.camunda.optimize.service.db.DatabaseConstants.MAPPING_PROPERTY_TYPE;
import static io.camunda.optimize.service.db.DatabaseConstants.TYPE_KEYWORD;

import io.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;
import java.io.IOException;
import java.util.Locale;
import org.elasticsearch.xcontent.XContentBuilder;

public class ProcessInstanceArchiveIndexV8 extends DefaultIndexMappingCreator<XContentBuilder> {
  public static final int VERSION = 8; // same as current processInstanceIndexVersion

  private final String indexName;

  public ProcessInstanceArchiveIndexV8(final String processInstanceIndexKey) {
    indexName = constructIndexName(processInstanceIndexKey);
  }

  public static String constructIndexName(final String processInstanceIndexKey) {
    return "process-instance-archive-" + processInstanceIndexKey.toLowerCase(Locale.ENGLISH);
  }

  @Override
  public String getIndexName() {
    return indexName;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public XContentBuilder addStaticSetting(String key, int value, XContentBuilder contentBuilder)
      throws IOException {
    return contentBuilder.field(key, value);
  }

  @Override
  public XContentBuilder addProperties(XContentBuilder xContentBuilder) throws IOException {
    // adding just one field since this Index exists to test index deletion only
    return xContentBuilder
        .startObject("processDefinitionKey")
        .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
        .endObject();
  }
}
