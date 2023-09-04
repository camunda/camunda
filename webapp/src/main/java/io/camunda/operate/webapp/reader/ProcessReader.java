/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.reader;

import io.camunda.operate.entities.ProcessEntity;
import io.camunda.operate.webapp.rest.dto.ProcessRequestDto;

import java.util.List;
import java.util.Map;

public interface ProcessReader {
    String getDiagram(Long processDefinitionKey);

    ProcessEntity getProcess(Long processDefinitionKey);

    Map<String, List<ProcessEntity>> getProcessesGrouped(ProcessRequestDto request);

    Map<Long, ProcessEntity> getProcesses();

    Map<Long, ProcessEntity> getProcessesWithFields(int maxSize, String... fields);

    Map<Long, ProcessEntity> getProcessesWithFields(String... fields);
}
