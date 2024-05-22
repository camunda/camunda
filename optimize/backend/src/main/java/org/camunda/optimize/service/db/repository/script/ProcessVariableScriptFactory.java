/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.repository.script;

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
