/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.schema.templates;

import org.springframework.stereotype.Component;

@Component
public class BatchOperationTemplate extends AbstractTemplateDescriptor {

  public static final String INDEX_NAME = "batch-operation";

  public static final String ID = "id";
  public static final String TYPE = "type";
  public static final String NAME = "name";
  public static final String USERNAME = "username";
  public static final String START_DATE = "startDate";
  public static final String END_DATE = "endDate";
  public static final String INSTANCES_COUNT = "instancesCount";
  public static final String OPERATIONS_TOTAL_COUNT = "operationsTotalCount";
  public static final String OPERATIONS_FINISHED_COUNT = "operationsFinishedCount";

  @Override
  public String getIndexName() {
    return INDEX_NAME;
  }

}
