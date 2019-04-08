/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer.variable;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.text.StringSubstitutor;
import org.camunda.optimize.dto.optimize.importing.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableDto;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class VariableUpdateWriter extends VariableWriter {

  @Autowired
  public VariableUpdateWriter(RestHighLevelClient esClient,
                              ObjectMapper objectMapper, DateTimeFormatter dateTimeFormatter) {
    super(esClient, objectMapper, dateTimeFormatter);
  }

  @Override
  protected String createInlineUpdateScript(Map<String, List<VariableDto>> typeMappedVars) {
    StringBuilder builder = new StringBuilder();
    Map<String, String> valuesMap = new HashMap<>();
    for (Map.Entry<String, List<VariableDto>> typedVarsEntry : typeMappedVars.entrySet()) {
      valuesMap.put("typeName", typedVarsEntry.getKey());
      final StringSubstitutor sub = new StringSubstitutor(valuesMap);
      // @formatter:off
      String variableScript =
        "HashMap ${typeName}Entries = new HashMap();" +
        "for (def var : ctx._source.${typeName}) {" +
          "${typeName}Entries.put(var.id, var);" +
        "}" +
        "for (def var : params.${typeName}) {" +
          "${typeName}Entries.compute(var.id, (k, v) -> { " +
          "  if (v == null) {" +
          "    return var;"   +
          "  } else {" +
          "    return v.version > var.version? v : var;" +
          "  }" +
          "});" +
        "}" +
        "ctx._source.${typeName} = ${typeName}Entries.values();\n";
      // @formatter:on
      String resolvedVariableScript = sub.replace(variableScript);
      builder.append(resolvedVariableScript);
    }
    return builder.toString();
  }

  @Override
  protected String getNewProcessInstanceRecordString(String processInstanceId,
                                                     Map<String, List<VariableDto>> typeMappedVars) throws
                                                                                                    JsonProcessingException {

    VariableDto firstVariable = grabFirstOne(typeMappedVars);
    if (firstVariable == null) {
      //all is lost, no variables to persist, should have crashed before.
      return null;
    }

    ProcessInstanceDto procInst = new ProcessInstanceDto();
    procInst.setProcessDefinitionId(firstVariable.getProcessDefinitionId());
    procInst.setProcessDefinitionKey(firstVariable.getProcessDefinitionKey());
    procInst.setProcessInstanceId(processInstanceId);
    procInst.setEngine(firstVariable.getEngineAlias());

    for (Map.Entry<String, List<VariableDto>> entry : typeMappedVars.entrySet()) {
      for (VariableDto var : entry.getValue()) {
        parseValue(var)
          .ifPresent(procInst::addVariableInstance);
      }
    }
    return objectMapper.writeValueAsString(procInst);
  }
}
