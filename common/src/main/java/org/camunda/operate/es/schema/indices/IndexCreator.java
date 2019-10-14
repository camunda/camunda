/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.es.schema.indices;

import java.io.IOException;
import org.elasticsearch.common.xcontent.XContentBuilder;

public interface IndexCreator {

  String getIndexName();

  String getAlias();

  XContentBuilder getSource() throws IOException;

  boolean needsSeveralShards();

}
