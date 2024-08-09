/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.migrate313to86.indices;

import io.camunda.optimize.service.db.schema.index.ProcessDefinitionIndex;
import java.io.IOException;
import org.elasticsearch.xcontent.XContentBuilder;

public class EventProcessDefinitionIndexV5 extends ProcessDefinitionIndex<XContentBuilder> {

  @Override
  public XContentBuilder addStaticSetting(
      final String key, final int value, final XContentBuilder contentBuilder) throws IOException {
    return contentBuilder.field(key, value);
  }

  @Override
  public String getIndexName() {
    return "event-process-definition";
  }

  @Override
  public int getVersion() {
    return 5;
  }
}
