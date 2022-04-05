/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.schema.indices;

import org.springframework.stereotype.Component;

@Component
public class FormIndex extends AbstractIndexDescriptor {

  public static final String INDEX_NAME = "form";

  public static final String ID = "id";
  public static final String BPMN_ID = "bpmnId";
  public static final String PROCESS_DEFINITION_ID = "processDefinitionId";
  public static final String SCHEMA = "schema";

  @Override
  public String getIndexName() {
    return INDEX_NAME;
  }
}
