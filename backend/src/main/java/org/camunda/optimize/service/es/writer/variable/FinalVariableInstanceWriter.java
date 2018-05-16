package org.camunda.optimize.service.es.writer.variable;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.camunda.optimize.dto.optimize.importing.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableDto;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Component
public class FinalVariableInstanceWriter extends VariableWriter {

  public void flagProcessInstancesAllVariablesHaveBeenImportedFor(List<String> processDefinitionIds) {
    logger.debug("Marking [{}] process instance that all variables have been imported", processDefinitionIds.size());
    BulkRequestBuilder variableBulkRequest = esclient.prepareBulk();

    for (String processInstanceId : processDefinitionIds) {
      Script updateScript = new Script(
        ScriptType.INLINE,
        Script.DEFAULT_SCRIPT_LANG,
        "ctx._source.allVariablesImported = true; ",
        new HashMap<>()
      );

      variableBulkRequest.add(
        esclient.prepareUpdate(
          configurationService.getOptimizeIndex(configurationService.getProcessInstanceType()),
          configurationService.getProcessInstanceType(),
          processInstanceId
        )
        .setScript(updateScript)
      );
    }

    if (variableBulkRequest.numberOfActions() != 0) {
      variableBulkRequest.get();
    }
  }

  @Override
  protected String createInlineUpdateScript(Map<String, List<VariableDto>> typeMappedVars) {
    StringBuilder builder = new StringBuilder();
    builder.append("if(!ctx._source.allVariablesImported) {\n"); // we need to ensure that variables are not added twice
      for (Map.Entry<String, List<VariableDto>> typedVarsEntry : typeMappedVars.entrySet()) {
        String typeName = typedVarsEntry.getKey();
        builder.append("ctx._source." + typeName + ".clear();\n");
        builder.append("ctx._source." + typeName + ".addAll(params." + typeName + ");\n");
      }
      builder.append("ctx._source.allVariablesImported=true; \n");
    builder.append("}");
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
    procInst.setAllVariablesImported(true);
    for (Map.Entry<String, List<VariableDto>> entry: typeMappedVars.entrySet()) {
      for (VariableDto var : entry.getValue()) {
        parseValue(var)
        .ifPresent(procInst::addVariableInstance);
      }
    }
    return objectMapper.writeValueAsString(procInst);
  }
}
