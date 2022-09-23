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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto.Modification;
import static io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto.Modification.Type.*;
import static java.util.function.Predicate.not;

@Component
public class ModifyProcessInstanceHandler extends AbstractOperationHandler implements OperationHandler {

    private static final Logger logger = LoggerFactory.getLogger(ModifyProcessInstanceHandler.class);

    @Autowired ZeebeClient zeebeClient;
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
        modifyVariables(processInstanceKey, getVariableModifications(modifications));
        modifyTokens(processInstanceKey, getTokenModifications(modifications));
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

    private void modifyTokens(final Long processInstanceKey, final List<Modification> modifications){
        // 0. Prepare
        if(modifications.isEmpty()) return;
        // 1. Add tokens
        final List<Modification> addTokens = modifications.stream().filter( m -> m.getModification().equals(ADD_TOKEN))
            .collect(Collectors.toList());
        addTokens.forEach( m -> addToken(processInstanceKey, m));
        // 2. Cancel tokens
        final List<Modification> cancelTokens = modifications.stream().filter( m -> m.getModification().equals(CANCEL_TOKEN))
            .collect(Collectors.toList());
        cancelTokens.forEach( m -> cancelToken(processInstanceKey, m));
        // 3. Move tokens
        final List<Modification> moveTokens = modifications.stream().filter( m -> m.getModification().equals(MOVE_TOKEN))
            .collect(Collectors.toList());
        moveTokens.forEach(m -> moveToken(processInstanceKey, m));
    }

    private void addToken(final Long processInstanceKey, final Modification modification){
        // 0. Prepare
        final String flowNodeId = modification.getToFlowNodeId();
        final Map<String,List<Map<String,Object>>> flowNodeId2variables = modification.variablesForAddToken();
        final ModifyProcessInstanceCommandStep1 startStep = zeebeClient.newModifyProcessInstanceCommand(processInstanceKey);
        ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep3 nextStep;
        // 1. Activate
        if(modification.getScopeKey() != null){
            nextStep = startStep.activateElement(flowNodeId, modification.getScopeKey());
        }else{
            nextStep = startStep.activateElement(flowNodeId);
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
        nextStep.send().join();
    }

    private void cancelToken(final Long processInstanceKey, final Modification modification){
        final String flowNodeId = modification.getFromFlowNodeId();
        final List<Long> flowNodeInstanceKeys = getNotFinishedFlowNodeInstanceKeysFor(processInstanceKey, flowNodeId);
        cancelFlowNodeInstances(processInstanceKey, flowNodeId, flowNodeInstanceKeys);
    }

    private void moveToken(final Long processInstanceKey, final Modification modification){
        // 0. Prepare
        final String toFlowNodeId = modification.getToFlowNodeId();
        final String fromFlowNodeId = modification.getFromFlowNodeId();
        Integer newTokensCount = modification.getNewTokensCount();
        // Add least one token will be added
        if(newTokensCount == null){
            newTokensCount = 1;
        }
        // flowNodeId => List of variables (Map)
        //  flowNodeId => [ { "key": "value" }, {"key for another flowNode with the same id": "value"} ]
        Map<String,List<Map<String,Object>>> flowNodeId2variables = modification.variablesForAddToken();
        if(flowNodeId2variables == null){
            flowNodeId2variables = new HashMap<>();
        }
        final List<Long> flowNodeInstanceKeysToCancel = getNotFinishedFlowNodeInstanceKeysFor(processInstanceKey, fromFlowNodeId);

        // 1. Add tokens
        final ModifyProcessInstanceCommandStep1 startStep = zeebeClient.newModifyProcessInstanceCommand(processInstanceKey);
        ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep3 nextStep = null;
        // Create flowNodes with variables
        final List<Map<String,Object>> toFlowNodeIdVariables = flowNodeId2variables.getOrDefault(toFlowNodeId, List.of());
        for(int i = 0;i < newTokensCount; i++){
            if(nextStep == null){
                nextStep = startStep.activateElement(toFlowNodeId);
            }else{
                nextStep = nextStep.and().activateElement(toFlowNodeId);
            }
            if(i < toFlowNodeIdVariables.size()){
                nextStep = nextStep.withVariables(toFlowNodeIdVariables.get(i), toFlowNodeId);
            }
        }
        if( nextStep != null) {
            for(String flowNodeId: flowNodeId2variables.keySet()){
                if(!flowNodeId.equals(toFlowNodeId)){
                    List<Map<String,Object>> flowNodeVars = flowNodeId2variables.get(flowNodeId);
                    for(Map<String,Object> vars: flowNodeVars){
                        nextStep.withVariables(vars, flowNodeId);
                    }
                }
            }
            nextStep.send().join();
        }
        // 2. cancel
        cancelFlowNodeInstances(processInstanceKey, fromFlowNodeId, flowNodeInstanceKeysToCancel);
    }

    private void cancelFlowNodeInstances(final Long processInstanceKey, final String flowNodeId, final List<Long> flowNodeInstanceKeys) {
        final int size = flowNodeInstanceKeys.size();
        if(size == 0){
            logger.warn("No flow node instance keys found for process instance {} and flow node id {}.",
                processInstanceKey, flowNodeId);
            return;
        }
        ModifyProcessInstanceCommandStep1 cancelStep = zeebeClient
            .newModifyProcessInstanceCommand(processInstanceKey);
        for(int i = 0; i < size - 1; i++){
            cancelStep = cancelStep.terminateElement(flowNodeInstanceKeys.get(i)).and();
        }
        cancelStep
            .terminateElement(flowNodeInstanceKeys.get(size - 1))
            .send().join();
    }
}
