/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.zeebe.operation;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.entities.FlowNodeState;
import io.camunda.operate.entities.OperationEntity;
import io.camunda.operate.entities.OperationType;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.util.OperationsManager;
import io.camunda.operate.webapp.es.reader.FlowNodeInstanceReader;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.ModifyProcessInstanceCommandStep1;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto.Modification;
import static io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto.Modification.Type.*;
import static java.util.function.Predicate.not;
// Modify Process Instance Implementation to execute all given modifications in one Zeebe 'transaction'
// So for one operation we have only one 'zeebeClient.send().join()'
@Component
public class SingleStepModifyProcessInstanceHandler extends AbstractOperationHandler implements ModifyProcessInstanceHandler {

    private static final Logger logger = LoggerFactory.getLogger(SingleStepModifyProcessInstanceHandler.class);
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private OperationsManager operationsManager;
    @Autowired
    private FlowNodeInstanceReader flowNodeInstanceReader;

    @Override
    public void handleWithException(final OperationEntity operation) throws Exception {
        final ModifyProcessInstanceRequestDto modifyProcessInstanceRequest =
            objectMapper.readValue(operation.getModifyInstructions(), ModifyProcessInstanceRequestDto.class);
        final Long processInstanceKey = Long.parseLong(modifyProcessInstanceRequest.getProcessInstanceKey());
        final List<Modification> modifications = modifyProcessInstanceRequest.getModifications();
        // Variables first
        modifyVariables(processInstanceKey, getVariableModifications(modifications));
        // Token Modifications in given order
        List<Modification> tokenModifications = getTokenModifications(modifications);

        ModifyProcessInstanceCommandStep1 currentStep = zeebeClient.newModifyProcessInstanceCommand(processInstanceKey);
        ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep2 lastStep = null;
        final int lastModificationIndex = tokenModifications.size() - 1;
        for(int i=0;i<tokenModifications.size();i++){
            Modification modification = tokenModifications.get(i);
            if(modification.getModification().equals(ADD_TOKEN)){
                var nextStep = addToken(currentStep, modification);
                if(i < lastModificationIndex){
                    currentStep = nextStep.and();
                }else{
                    lastStep = nextStep;
                }
            }else if(modification.getModification().equals(CANCEL_TOKEN)){
                var nextStep = cancelToken(currentStep, processInstanceKey, modification);
                if(i < lastModificationIndex){
                    currentStep = nextStep.and();
                }else{
                    lastStep = nextStep;
                }
            } else if(modification.getModification().equals(MOVE_TOKEN)){
                var nextStep = moveToken(currentStep, processInstanceKey, modification);
                if(i < lastModificationIndex){
                    currentStep = nextStep.and();
                }else{
                    lastStep = nextStep;
                }
            }
        }
        if(lastStep != null){
            lastStep.send().join();
        }
        markAsSent(operation);
        completeOperation(operation);
    }

    private void completeOperation(final OperationEntity operation) throws PersistenceException {
        operationsManager.completeOperation(operation);
    }

    private void modifyVariables(final Long processInstanceKey, final List<Modification> modifications) {
        modifications.forEach(modification -> {
            final Long scopeKey = determineScopeKey(processInstanceKey, modification);
            zeebeClient
                .newSetVariablesCommand(scopeKey)
                .variables(modification.getVariables())
                .local(true)
                .send().join();
        });
    }

    private Long determineScopeKey(final Long processInstanceKey, final Modification modification) {
        return modification.getScopeKey() == null ? processInstanceKey : modification.getScopeKey();
    }

    private List<Long> getNotFinishedFlowNodeInstanceKeysFor(final Long processInstanceKey, final String  flowNodeId) {
        return flowNodeInstanceReader.getFlowNodeInstanceKeysByIdAndStates(
            processInstanceKey, flowNodeId, List.of(FlowNodeState.ACTIVE));
    }

    @Override
    public Set<OperationType> getTypes() {
        return Set.of(OperationType.MODIFY_PROCESS_INSTANCE);
    }

    // Needed for tests
    public void setZeebeClient(final ZeebeClient zeebeClient) {
        this.zeebeClient = zeebeClient;
    }

    private List<Modification> getVariableModifications(final List<Modification> modifications){
        return modifications.stream()
            .filter(this::isVariableModification)
            .collect(Collectors.toList());
    }

    private List<Modification> getTokenModifications(final List<Modification> modifications){
        return modifications.stream()
            .filter(not(this::isVariableModification))
            .collect(Collectors.toList());
    }
    private boolean isVariableModification(final Modification modification){
        return
            modification.getModification().equals(ADD_VARIABLE) ||
            modification.getModification().equals(EDIT_VARIABLE);
    }

    private ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep3 addToken(ModifyProcessInstanceCommandStep1 currentStep, final Modification modification){
        // 0. Prepare
        final String flowNodeId = modification.getToFlowNodeId();
        final Map<String,List<Map<String,Object>>> flowNodeId2variables = modification.variablesForAddToken();
        logger.debug("Add token to flowNodeId {} with variables: {}", flowNodeId, flowNodeId2variables);
        ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep3 nextStep;
        // 1. Activate
        if(modification.getAncestorElementInstanceKey() != null){
            nextStep = currentStep.activateElement(flowNodeId, modification.getAncestorElementInstanceKey());
        }else{
            nextStep = currentStep.activateElement(flowNodeId);
        }
        // 2. Add variables
        if(flowNodeId2variables != null){
            for(String scopeId: flowNodeId2variables.keySet()){
                final List<Map<String,Object>> variablesForFlowNode = flowNodeId2variables.get(scopeId);
                for(Map<String,Object> vars: variablesForFlowNode){
                    nextStep = nextStep.withVariables(vars, scopeId);
                }
            }
        }
        return nextStep;
    }

    private ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep2 cancelToken(ModifyProcessInstanceCommandStep1 currentStep, final Long processInstanceKey, final Modification modification){
        final String flowNodeId = modification.getFromFlowNodeId();
        final String flowNodeInstanceKeyAsString = modification.getFromFlowNodeInstanceKey();
        if(StringUtils.hasText(flowNodeInstanceKeyAsString)) {
           final Long  flowNodeInstanceKey =  Long.parseLong(flowNodeInstanceKeyAsString);
           logger.debug("Cancel token from flowNodeInstanceKey {} ", flowNodeInstanceKey);
           return cancelFlowNodeInstances(currentStep, List.of(flowNodeInstanceKey));
        } else {
           final List<Long> flowNodeInstanceKeys = getNotFinishedFlowNodeInstanceKeysFor(processInstanceKey, flowNodeId);
           if(flowNodeInstanceKeys.isEmpty()){
              throw new OperateRuntimeException(
                 String.format(
                    "Abort CANCEL_TOKEN: Can't find not finished flowNodeInstance keys for process instance %s and flowNode id %s"
                    , processInstanceKey, flowNodeId));
           }
           logger.debug("Cancel token from flowNodeInstanceKeys {} ", flowNodeInstanceKeys);
           return cancelFlowNodeInstances(currentStep, flowNodeInstanceKeys);
        }
    }

    private ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep2 moveToken(ModifyProcessInstanceCommandStep1 currentStep, final Long processInstanceKey, final Modification modification){
        // 0. Prepare
        final String toFlowNodeId = modification.getToFlowNodeId();
        Integer newTokensCount = modification.getNewTokensCount();
        // Add least one token will be added
        if (newTokensCount == null || newTokensCount < 1) {
            newTokensCount = 1;
        }
        // flowNodeId => List of variables (Map)
        //  flowNodeId => [ { "key": "value" }, {"key for another flowNode with the same id": "value"} ]
        Map<String,List<Map<String,Object>>> flowNodeId2variables = modification.variablesForAddToken();
        if (flowNodeId2variables == null){
            flowNodeId2variables = new HashMap<>();
        }

        // 1. Add tokens
        ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep3 nextStep = null;
        // Create flowNodes with variables
        final List<Map<String,Object>> toFlowNodeIdVariables = flowNodeId2variables.getOrDefault(toFlowNodeId, List.of());
        logger.debug("Move [Add token to flowNodeId: {} with variables: {} ]", toFlowNodeId, toFlowNodeIdVariables);
        for(int i = 0;i < newTokensCount; i++){
            if(nextStep == null){
                if (modification.getAncestorElementInstanceKey() != null ){
                    nextStep = currentStep.activateElement(toFlowNodeId, modification.getAncestorElementInstanceKey());
                } else {
                    nextStep = currentStep.activateElement(toFlowNodeId);
                }
            }else{
                if (modification.getAncestorElementInstanceKey() != null ) {
                    nextStep.and().activateElement(toFlowNodeId, modification.getAncestorElementInstanceKey());
                } else {
                    nextStep = nextStep.and().activateElement(toFlowNodeId);
                }
            }
            if(i < toFlowNodeIdVariables.size()){
                nextStep = nextStep.withVariables(toFlowNodeIdVariables.get(i), toFlowNodeId);
            }
        }
        for(final String flowNodeId: flowNodeId2variables.keySet()){
            if(!flowNodeId.equals(toFlowNodeId)){
                final List<Map<String,Object>> flowNodeVars = flowNodeId2variables.get(flowNodeId);
                for(Map<String,Object> vars: flowNodeVars){
                    nextStep.withVariables(vars, flowNodeId);
                }
            }
        }
        // 2. cancel
        final String fromFlowNodeId = modification.getFromFlowNodeId();
        final String fromFlowNodeInstanceKey = modification.getFromFlowNodeInstanceKey();
        List<Long> flowNodeInstanceKeysToCancel;
        if(StringUtils.hasText(fromFlowNodeInstanceKey)){
            final Long flowNodeInstanceKey = Long.parseLong(fromFlowNodeInstanceKey);
            flowNodeInstanceKeysToCancel = List.of(flowNodeInstanceKey);
        }else {
            flowNodeInstanceKeysToCancel = getNotFinishedFlowNodeInstanceKeysFor(processInstanceKey, fromFlowNodeId);
            if(flowNodeInstanceKeysToCancel.isEmpty()){
                throw new OperateRuntimeException(
                    String.format(
                        "Abort MOVE_TOKEN (CANCEL step): Can't find not finished flowNodeInstance keys for process instance %s and flowNode id %s"
                        , processInstanceKey, fromFlowNodeId));
            }
        }
        logger.debug("Move [Cancel token from flowNodeInstanceKeys: {} ]", flowNodeInstanceKeysToCancel);
        return cancelFlowNodeInstances(nextStep.and(), flowNodeInstanceKeysToCancel);
    }

    private ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep2 cancelFlowNodeInstances(ModifyProcessInstanceCommandStep1 currentStep, final List<Long> flowNodeInstanceKeys) {
        ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep2 nextStep = null;
        final int size = flowNodeInstanceKeys.size();
        for(int i = 0; i < size; i++){
            if(i < size - 1){
                currentStep = currentStep.terminateElement(flowNodeInstanceKeys.get(i)).and();
            }else{
                nextStep = currentStep.terminateElement(flowNodeInstanceKeys.get(i));
            }
        }
        return nextStep;
    }
}
