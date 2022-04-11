/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.schema;

import org.elasticsearch.xcontent.XContentBuilder;

import java.io.IOException;

public interface PropertiesAppender {

  XContentBuilder addProperties(XContentBuilder xContentBuilder) throws IOException;
}
