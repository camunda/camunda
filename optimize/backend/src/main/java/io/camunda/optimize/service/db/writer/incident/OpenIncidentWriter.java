/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.writer.incident;

public interface OpenIncidentWriter extends AbstractIncidentWriter {

  @Override
  default String createInlineUpdateScript() {
    // already imported incidents should win over the
    // new instances, since the stored instances are
    // probably completed incidents.
    return """
            def existingIncidentsById = ctx._source.incidents.stream().collect(Collectors.toMap(e -> e.id, e -> e, (e1, e2) -> e1));
            def incidentsToAddById = params.incidents.stream().filter(e -> !existingIncidentsById.containsKey(e.id))
            .collect(Collectors.toMap(e -> e.id, e -> e, (e1, e2) -> e1));
            ctx._source.incidents.addAll(incidentsToAddById.values());
        """;
  }
}
