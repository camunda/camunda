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
            String.format("Unknown Modification.Type given for process instance with key %s", processInstanceReader));
      }
    });
  }

  private void validateAddEditVariable(final Modification modification, final Long processInstanceKey) {
    if( !MapUtils.isNotEmpty(modification.getVariables()) ){
      throw new InvalidRequestException(String.format("No variables given for process instance with key %s", processInstanceKey));
    }
  }

  private void validateMoveToken(final Modification modification, final Long processInstanceKey) {
    if(modification.getFromFlowNodeId()  == null || modification.getToFlowNodeId() == null){
      throw new InvalidRequestException(String.format("MOVE_TOKEN needs fromFlowNodeId and toFlowNodeId for process instance with key %s", processInstanceKey));
    }
  }

  private void validateCancelToken(final Modification modification, final Long processInstanceKey) {
    if(modification.getFromFlowNodeId() == null){
      throw new InvalidRequestException(String.format("No fromFlowNodeId given for process instance with key %s", processInstanceKey));
    }
  }

  private void validateAddToken(final Modification modification, final Long processInstanceKey){
    if(modification.getToFlowNodeId() == null){
      throw new InvalidRequestException(String.format("No toFlowNodeId given for process instance with key %s", processInstanceKey));
    }
  }
}
