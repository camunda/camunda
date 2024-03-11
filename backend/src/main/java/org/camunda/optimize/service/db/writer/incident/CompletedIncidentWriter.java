/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.writer.incident;

public interface CompletedIncidentWriter extends AbstractIncidentWriter {

  @Override
  default String createInlineUpdateScript() {
    // new import incidents should win over already
    // imported incidents, since those might be open incidents
    return
    """
        def existingIncidentsById = ctx._source.incidents.stream().collect(Collectors.toMap(e -> e.id, e -> e, (e1, e2) -> e1));
        def incidentsToAddById = params.incidents.stream().collect(Collectors.toMap(e -> e.id, e -> e, (e1, e2) -> e1));
        existingIncidentsById.putAll(incidentsToAddById);
        ctx._source.incidents = existingIncidentsById.values();
        """;
  }
}
