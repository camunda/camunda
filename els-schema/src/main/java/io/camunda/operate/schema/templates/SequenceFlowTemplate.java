/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.schema.templates;

import org.springframework.stereotype.Component;

@Component
public class SequenceFlowTemplate extends AbstractTemplateDescriptor implements ProcessInstanceDependant {

  public static final String INDEX_NAME = "sequence-flow";

  public static final String ID = "id";
  public static final String KEY = "key";
  public static final String PROCESS_INSTANCE_KEY = "processInstanceKey";
  public static final String ACTIVITY_ID = "activityId";

  @Override
  public String getIndexName() {
    return INDEX_NAME;
  }

}
