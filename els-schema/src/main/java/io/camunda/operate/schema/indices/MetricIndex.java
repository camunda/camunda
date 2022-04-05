/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.schema.indices;

import org.springframework.stereotype.Component;

@Component
public class MetricIndex extends AbstractIndexDescriptor {

  public static final String INDEX_NAME = "metric";
  public static final String ID = "id";
  public static final String EVENT = "event";
  public static final String VALUE = "value";
  public static final String EVENT_TIME = "eventTime";

  @Override
  public String getIndexName() {
    return INDEX_NAME;
  }
}
