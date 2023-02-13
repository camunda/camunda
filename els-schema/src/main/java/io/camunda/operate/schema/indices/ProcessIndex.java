/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.schema.indices;

import io.camunda.operate.schema.backup.Prio4Backup;
import org.springframework.stereotype.Component;

@Component
public class ProcessIndex extends AbstractIndexDescriptor  implements Prio4Backup {

  public static final String INDEX_NAME = "process";
  public static final String ID = "id";
  public static final String KEY = "key";
  public static final String BPMN_PROCESS_ID = "bpmnProcessId";
  public static final String NAME = "name";
  public static final String VERSION = "version";
  public static final String BPMN_XML = "bpmnXml";
  public static final String RESOURCE_NAME = "resourceName";
  public static final String FLOWNODES = "flowNodes";
  public static final String FLOWNODE_ID = "id";
  public static final String FLOWNODE_NAME = "name";

  @Override
  public String getIndexName() {
    return INDEX_NAME;
  }

  @Override
  public String getVersion() {
    return "8.1.7";
  }
}
