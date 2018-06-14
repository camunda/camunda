package org.camunda.optimize.service.es.writer.variable;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.commons.text.StrSubstitutor;
import org.camunda.optimize.dto.optimize.importing.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableDto;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class VariableUpdateWriter extends VariableWriter {

  @Override
  protected String createInlineUpdateScript(Map<String, List<VariableDto>> typeMappedVars) {
    StringBuilder builder = new StringBuilder();
    Map<String, String> valuesMap = new HashMap<>();
    for (Map.Entry<String, List<VariableDto>> typedVarsEntry : typeMappedVars.entrySet()) {
      valuesMap.put("typeName", typedVarsEntry.getKey());
      StrSubstitutor sub = new StrSubstitutor(valuesMap);
      String variableScript =
        "HashMap ${typeName}Entries = new HashMap();" +
        "for (def var : ctx._source.${typeName}) {" +
          "${typeName}Entries.put(var.id, var);" +
        "}" +
        "for (def var : params.${typeName}) {" +
          "${typeName}Entries.put(var.id, var);" +
        "}" +
        "ctx._source.${typeName} = ${typeName}Entries.values();\n";
      String resolvedVariableScript = sub.replace(variableScript);
      builder.append(resolvedVariableScript);
    }
    return builder.toString();
  }

  @Override
  protected String getNewProcessInstanceRecordString(String processInstanceId,
                                                   Map<String, List<VariableDto>> typeMappedVars) throws JsonProcessingException {

    VariableDto firstVariable = grabFirstOne(typeMappedVars);
    if (firstVariable == null) {
      //all is lost, no variables to persist, should have crashed before.
      return null;
    }

    ProcessInstanceDto procInst = new ProcessInstanceDto();
    procInst.setProcessDefinitionId(firstVariable.getProcessDefinitionId());
    procInst.setProcessDefinitionKey(firstVariable.getProcessDefinitionKey());
    procInst.setProcessInstanceId(processInstanceId);

    for (Map.Entry<String, List<VariableDto>> entry: typeMappedVars.entrySet()) {
      for (VariableDto var : entry.getValue()) {
        parseValue(var)
        .ifPresent(procInst::addVariableInstance);
      }
    }
    return objectMapper.writeValueAsString(procInst);
  }
}
