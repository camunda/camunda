package org.camunda.optimize.service.es.writer.variable;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.camunda.optimize.dto.optimize.importing.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableDto;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Component
public class VariableUpdateWriter extends VariableWriter {

  @Override
  protected String createInlineUpdateScript(Map<String, List<VariableDto>> typeMappedVars) {
    StringBuilder builder = new StringBuilder();
    for (Map.Entry<String, List<VariableDto>> typedVarsEntry : typeMappedVars.entrySet()) {
      String typeName = typedVarsEntry.getKey();
      builder.append("for (def var : params." + typeName + ") {");
        builder.append("ctx._source." + typeName + ".removeIf(item -> item.id.equals(var.id) || var.value == null) ;");
      builder.append("}");
      // if a variable update has the value null then this means that the runtime variable
      // has been deleted and therefore we shouldn't add it to Optimize
      builder.append("params." + typeName + ".removeIf(item -> item.value == null);");
      builder.append("ctx._source." + typeName + ".addAll(params." + typeName + ");\n");
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
    procInst.setStartDate(OffsetDateTime.now());
    procInst.setEndDate(OffsetDateTime.now());
    for (Map.Entry<String, List<VariableDto>> entry: typeMappedVars.entrySet()) {
      for (VariableDto var : entry.getValue()) {
        parseValue(var)
        .ifPresent(procInst::addVariableInstance);
      }
    }
    return objectMapper.writeValueAsString(procInst);
  }
}
