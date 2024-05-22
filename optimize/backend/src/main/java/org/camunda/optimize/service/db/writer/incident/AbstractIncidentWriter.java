/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.writer.incident;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.persistence.incident.IncidentDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface AbstractIncidentWriter {

  Logger log = LoggerFactory.getLogger(AbstractIncidentWriter.class);

  default List<ImportRequestDto> generateIncidentImports(List<IncidentDto> incidents) {
    final String importItemName = "incidents";
    log.debug("Creating imports for {} [{}].", incidents.size(), importItemName);

    createInstanceIndicesFromIncidentsIfMissing(incidents);

    Map<String, List<IncidentDto>> processInstanceToEvents = new HashMap<>();
    for (IncidentDto e : incidents) {
      processInstanceToEvents.putIfAbsent(e.getProcessInstanceId(), new ArrayList<>());
      processInstanceToEvents.get(e.getProcessInstanceId()).add(e);
    }

    return processInstanceToEvents.entrySet().stream()
        .map(entry -> createImportRequestForIncident(entry, importItemName))
        .collect(Collectors.toList());
  }

  void createInstanceIndicesFromIncidentsIfMissing(final List<IncidentDto> incidents);

  ImportRequestDto createImportRequestForIncident(
      Map.Entry<String, List<IncidentDto>> incidentsByProcessInstance, final String importName);

  String createInlineUpdateScript();
}
