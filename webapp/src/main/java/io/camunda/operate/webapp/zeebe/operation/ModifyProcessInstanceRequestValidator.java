/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.zeebe.operation;

import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.webapp.es.reader.ProcessInstanceReader;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto;
import io.camunda.operate.webapp.rest.exception.InvalidRequestException;
import org.apache.commons.collections4.MapUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import static io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto.Modification;
@Component
public class ModifyProcessInstanceRequestValidator {

  @Autowired
  private ProcessInstanceReader processInstanceReader;

  public void validate(final ModifyProcessInstanceRequestDto modifyRequest){
    final Long processInstanceKey = Long.parseLong(modifyRequest.getProcessInstanceKey());
    if( processInstanceReader.getProcessInstanceByKey(processInstanceKey) == null) {
      throw new InvalidRequestException(String.format("Process instance with key %s does not exist", processInstanceKey));
    }
    if( !CollectionUtil.isNotEmpty(modifyRequest.getModifications())) {
      throw new InvalidRequestException(String.format("No modifications given for process instance with key %s", processInstanceKey));
    }
    modifyRequest.getModifications().forEach(modification -> {
      final ModifyProcessInstanceRequestDto.Modification.Type type = modification.getModification();
      if(type == ModifyProcessInstanceRequestDto.Modification.Type.ADD_TOKEN){
        validateAddToken(modification, processInstanceKey);
      }else if(type == ModifyProcessInstanceRequestDto.Modification.Type.CANCEL_TOKEN){
        validateCancelToken(modification, processInstanceKey);
      }else if(type == ModifyProcessInstanceRequestDto.Modification.Type.MOVE_TOKEN){
       validateMoveToken(modification, processInstanceKey);
      }else if(
          type == ModifyProcessInstanceRequestDto.Modification.Type.ADD_VARIABLE ||
              type == ModifyProcessInstanceRequestDto.Modification.Type.EDIT_VARIABLE){
        validateAddEditVariable(modification, processInstanceKey);
      }else {
        throw new InvalidRequestException(
            String.format("Unknown Modification.Type given for process instance with key %s.", processInstanceKey));
      }
    });
  }

  private void validateAddEditVariable(final Modification modification, final Long processInstanceKey) {
    if( !MapUtils.isNotEmpty(modification.getVariables()) ){
      throw new InvalidRequestException(String.format("No variables given for process instance with key %s", processInstanceKey));
    }
  }

  private void validateMoveToken(final Modification modification, final Long processInstanceKey) {
    validateAddToken(modification, processInstanceKey);
    validateCancelToken(modification,processInstanceKey);
  }

  private void validateCancelToken(final Modification modification, final Long processInstanceKey) {
    if (!StringUtils.hasText(modification.getFromFlowNodeId()) && !StringUtils.hasText(modification.getFromFlowNodeInstanceKey())) {
      throw new InvalidRequestException(String.format("Neither fromFlowNodeId nor fromFlowNodeInstanceKey is given for process instance with key %s", processInstanceKey));
    }
    if (StringUtils.hasText(modification.getFromFlowNodeId()) && StringUtils.hasText(modification.getFromFlowNodeInstanceKey())){
      throw new InvalidRequestException(String.format("Either fromFlowNodeId or fromFlowNodeInstanceKey for process instance with key %s should be given, not both.", processInstanceKey));
    }
    if (modification.getFromFlowNodeInstanceKey() != null) {
      try{
        Long.parseLong(modification.getFromFlowNodeInstanceKey());
      } catch (NumberFormatException nfe){
        throw new InvalidRequestException("fromFlowNodeInstanceKey should be a Long.");
      }
    }
  }

  private void validateAddToken(final Modification modification, final Long processInstanceKey){
    if(modification.getToFlowNodeId() == null){
      throw new InvalidRequestException(String.format("No toFlowNodeId given for process instance with key %s", processInstanceKey));
    }
  }
}
