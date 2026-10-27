/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository.script;

public interface ProcessVariableScriptFactory {
  static String createInlineUpdateScript() {
    return """
        HashMap varIdToVar = new HashMap();
        for (def existingVar : ctx._source.${variables}) {
        varIdToVar.put(existingVar.id, existingVar);
        }
        for (def newVar : params.${variableUpdatesFromEngine}) {
        varIdToVar.compute(newVar.id, (k, v) -> {
          if (v == null) {
            return newVar;
          } else {
            return v.version > newVar.version ? v : newVar;
          }
        });
        }
        ctx._source.${variables} = varIdToVar.values();
        """;
  }
}
