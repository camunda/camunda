/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.schema.index.events;

import io.camunda.optimize.service.db.schema.index.events.EventSequenceCountIndex;
import java.io.IOException;
import org.elasticsearch.xcontent.XContentBuilder;

public class EventSequenceCountIndexES extends EventSequenceCountIndex<XContentBuilder> {

  public EventSequenceCountIndexES(final String indexKey) {
    super(indexKey);
  }

  @Override
  public XContentBuilder addStaticSetting(String key, int value, XContentBuilder contentBuilder)
      throws IOException {
    return contentBuilder.field(key, value);
  }
}
