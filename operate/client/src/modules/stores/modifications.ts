/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {makeAutoObservable} from 'mobx';
import {generateUniqueID} from 'modules/utils/generateUniqueID';
import {type ModifyProcessInstanceRequestBody} from '@camunda/camunda-api-zod-schemas/8.9';
import {modifyProcessInstance} from 'modules/api/v2/processInstances/modifyProcessInstance';
import {logger} from 'modules/logger';
import {tracking} from 'modules/tracking';
import {getFlowNodeName} from 'modules/utils/flowNodes';
import {getFlowNodesInBetween} from 'modules/utils/processInstanceDetailsDiagram';
import type {BusinessObjects} from 'bpmn-js/lib/NavigatedViewer';
import {generateParentScopeIds} from 'modules/utils/modifications';

type ActivateInstruction = NonNullable<
  ModifyProcessInstanceRequestBody['activateInstructions']
>[number];
type MoveInstruction = NonNullable<
  ModifyProcessInstanceRequestBody['moveInstructions']
>[number];
type TerminateInstruction = NonNullable<
  ModifyProcessInstanceRequestBody['terminateInstructions']
>[number];
type VariableInstruction = NonNullable<
  ActivateInstruction['variableInstructions']
>[number];

type ScopeMap = {[internalScopeId: string]: string};

type AncestorScopeTypes = NonNullable<
  MoveInstruction['ancestorScopeInstruction']
>['ancestorScopeType'];

type AncestorScopeType =
  | Extract<AncestorScopeTypes, 'inferred' | 'sourceParent'>
  | undefined;

type FlowNodeModificationPayload =
  | {
      operation: 'ADD_TOKEN';
      scopeId: string;
      flowNode: {id: string; name: string};
      affectedTokenCount: number;
      visibleAffectedTokenCount: number;
      ancestorElement?: {
        instanceKey: string;
        flowNodeId: string;
      };
      parentScopeIds: {
        [flowNodeId: string]: string;
      };
    }
  | {
      operation: 'CANCEL_TOKEN';
      flowNode: {id: string; name: string};
      flowNodeInstanceKey?: string;
      affectedTokenCount: number;
      visibleAffectedTokenCount: number;
    }
  | {
      operation: 'MOVE_TOKEN';
      flowNode: {id: string; name: string};
      flowNodeInstanceKey?: string;
      affectedTokenCount: number;
      visibleAffectedTokenCount: number;
      targetFlowNode: {id: string; name: string};
      scopeIds: string[];
      parentScopeIds: {
        [flowNodeId: string]: string;
      };
      ancestorScopeType?: AncestorScopeType;
    };

type VariableModificationPayload = {
  operation: 'ADD_VARIABLE' | 'EDIT_VARIABLE';
  id: string;
  scopeId: string;
  flowNodeName: string;
  name: string;
  oldValue?: string;
  newValue: string;
};

type FlowNodeModification = {
  type: 'token';
  payload: FlowNodeModificationPayload;
};

type VariableModification = {
  type: 'variable';
  payload: VariableModificationPayload;
};

type Modification = FlowNodeModification | VariableModification;
type RemovedModificationSource = 'variables' | 'summaryModal' | 'footer';

type State = {
  status:
    | 'enabled'
    | 'adding-token'
    | 'moving-token'
    | 'disabled'
    | 'applying-modifications';
  modifications: Modification[];
  lastRemovedModification:
    | {
        modification: Modification | undefined;
        source: RemovedModificationSource;
      }
    | undefined;
  sourceFlowNodeIdForMoveOperation: string | null;
  sourceFlowNodeInstanceKeyForMoveOperation: string | null;
  sourceFlowNodeIdForAddOperation: string | null;
};

const DEFAULT_STATE: State = {
  status: 'disabled',
  modifications: [],
  sourceFlowNodeIdForMoveOperation: null,
  sourceFlowNodeInstanceKeyForMoveOperation: null,
  sourceFlowNodeIdForAddOperation: null,
  lastRemovedModification: undefined,
};

const EMPTY_MODIFICATION = Object.freeze({
  newTokens: 0,
  cancelledTokens: 0,
  visibleCancelledTokens: 0,
  cancelledChildTokens: 0,
  areAllTokensCanceled: false,
});

class Modifications {
  state: State = {...DEFAULT_STATE};
  modificationsLoadingTimeout: number | undefined;

  constructor() {
    makeAutoObservable(this);
  }

  startMovingToken = (
    sourceFlowNodeId: string,
    sourceFlowNodeInstanceKey?: string,
  ) => {
    this.setStatus('moving-token');
    this.setSourceFlowNodeIdForMoveOperation(sourceFlowNodeId);
    this.setSourceFlowNodeInstanceKeyForMoveOperation(
      sourceFlowNodeInstanceKey ?? null,
    );
  };

  startAddingToken = (sourceFlowNodeId: string) => {
    this.setStatus('adding-token');
    this.state.sourceFlowNodeIdForAddOperation = sourceFlowNodeId;
  };

  generateScopeIdsInBetween = (
    targetFlowNodeId: string,
    ancestorFlowNodeId: string,
    businessObjects: BusinessObjects,
  ) => {
    const flowNodesInBetween = getFlowNodesInBetween(
      businessObjects,
      targetFlowNodeId,
      ancestorFlowNodeId,
    );

    return flowNodesInBetween.reduce<{[flowNodeId: string]: string}>(
      (flowNodeScopes, flowNodeId) => {
        flowNodeScopes[flowNodeId] = generateUniqueID();
        return flowNodeScopes;
      },
      {},
    );
  };

  finishAddingToken = (
    businessObjects: BusinessObjects,
    ancestorElementId?: string,
    ancestorElementInstanceKey?: string,
  ) => {
    if (
      ancestorElementInstanceKey !== undefined &&
      ancestorElementId !== undefined &&
      this.state.sourceFlowNodeIdForAddOperation !== null
    ) {
      this.addModification({
        type: 'token',
        payload: {
          operation: 'ADD_TOKEN',
          scopeId: generateUniqueID(),
          flowNode: {
            id: this.state.sourceFlowNodeIdForAddOperation,
            name:
              getFlowNodeName({
                businessObjects,
                flowNodeId: this.state.sourceFlowNodeIdForAddOperation,
              }) ?? '',
          },
          affectedTokenCount: 1,
          visibleAffectedTokenCount: 1,
          ancestorElement: {
            instanceKey: ancestorElementInstanceKey,
            flowNodeId: ancestorElementId,
          },
          parentScopeIds: this.generateScopeIdsInBetween(
            this.state.sourceFlowNodeIdForAddOperation,
            ancestorElementId,
            businessObjects,
          ),
        },
      });
    }

    this.setStatus('enabled');
    this.state.sourceFlowNodeIdForAddOperation = null;
  };

  setStatus = (status: State['status']) => {
    this.state.status = status;
  };

  setSourceFlowNodeIdForMoveOperation = (sourceFlowNodeId: string | null) => {
    this.state.sourceFlowNodeIdForMoveOperation = sourceFlowNodeId;
  };

  setSourceFlowNodeInstanceKeyForMoveOperation = (
    sourceFlowNodeInstanceKey: string | null,
  ) => {
    this.state.sourceFlowNodeInstanceKeyForMoveOperation =
      sourceFlowNodeInstanceKey;
  };

  enableModificationMode = () => {
    tracking.track({
      eventName: 'enable-modification-mode',
    });
    this.setStatus('enabled');
  };

  disableModificationMode = () => {
    this.setStatus('disabled');
  };

  startApplyingModifications = () => {
    this.setStatus('applying-modifications');
  };

  addModification = (modification: Modification) => {
    this.state.modifications.push(modification);
  };

  removeLastModification = () => {
    this.state.lastRemovedModification = {
      modification: this.state.modifications.pop(),
      source: 'footer',
    };
  };

  removeFlowNodeModification = (
    flowNodeModification: FlowNodeModificationPayload,
  ) => {
    if (flowNodeModification.operation === 'ADD_TOKEN') {
      this.state.modifications = this.state.modifications.filter(
        ({type, payload}) =>
          !(
            type === 'token' &&
            payload.flowNode.id === flowNodeModification.flowNode.id &&
            payload.operation === flowNodeModification.operation &&
            payload.scopeId === flowNodeModification.scopeId
          ),
      );
    } else {
      this.state.modifications = this.state.modifications.filter(
        ({type, payload}) =>
          !(
            type === 'token' &&
            payload.flowNode.id === flowNodeModification.flowNode.id &&
            payload.operation === flowNodeModification.operation &&
            payload.flowNodeInstanceKey ===
              flowNodeModification.flowNodeInstanceKey
          ),
      );
    }
  };

  removeVariableModification = (
    scopeId: string,
    id: string,
    operation: 'ADD_VARIABLE' | 'EDIT_VARIABLE',
    source: RemovedModificationSource,
  ) => {
    const lastModification = this.getLastVariableModification(
      scopeId,
      id,
      operation,
    );

    if (lastModification === undefined) {
      return;
    }

    this.state.modifications = this.state.modifications.filter(
      ({type, payload}) =>
        !(
          type === 'variable' &&
          payload.scopeId === lastModification.scopeId &&
          payload.id === lastModification.id &&
          payload.operation === lastModification.operation
        ),
    );

    this.state.lastRemovedModification = {
      modification: {
        type: 'variable',
        payload: lastModification,
      },
      source,
    };
  };

  get isModificationModeEnabled() {
    return !['disabled', 'applying-modifications'].includes(this.state.status);
  }

  get isMoveAllOperation() {
    return (
      this.state.status === 'moving-token' &&
      this.state.sourceFlowNodeInstanceKeyForMoveOperation === null
    );
  }

  get lastModification() {
    const [lastModification] = this.state.modifications.slice(-1);

    return lastModification;
  }

  get variableModifications() {
    function isVariableModification(
      modification: Modification,
    ): modification is VariableModification {
      const {type} = modification;

      return type === 'variable';
    }

    const latestVariableModifications = this.state.modifications
      .filter(isVariableModification)
      .map(({payload}) => payload)
      .reduce<{
        [key: string]: VariableModificationPayload;
      }>((accumulator, modification) => {
        const {id, scopeId} = modification;
        accumulator[`${scopeId}-${id}`] = modification;
        return accumulator;
      }, {});

    return Object.values(latestVariableModifications);
  }

  get flowNodeModifications() {
    function isFlowNodeModification(
      modification: Modification,
    ): modification is FlowNodeModification {
      const {type} = modification;

      return type === 'token';
    }

    return this.state.modifications
      .filter(isFlowNodeModification)
      .map(({payload}) => payload);
  }

  getLastVariableModification = (
    flowNodeInstanceId: string | null,
    id: string,
    operation: 'ADD_VARIABLE' | 'EDIT_VARIABLE',
  ) => {
    return this.variableModifications.find(
      (modification) =>
        modification.operation === operation &&
        modification.scopeId === flowNodeInstanceId &&
        modification.id === id,
    );
  };

  getAddVariableModifications = (scopeId: string | null) => {
    if (scopeId === null) {
      return [];
    }

    return this.variableModifications
      .filter(
        (modification) =>
          modification.operation === 'ADD_VARIABLE' &&
          modification.scopeId === scopeId,
      )
      .map(({name, newValue, id}) => ({
        name,
        value: newValue,
        id,
      }));
  };

  #getVariablesForScope(scopeId: string) {
    const variableModifications = this.variableModifications.filter(
      (modification) => modification.scopeId === scopeId,
    );

    if (variableModifications.length === 0) {
      return undefined;
    }

    return variableModifications.reduce<{[key: string]: string}>(
      (accumulator, {name, newValue}) => {
        accumulator[name] = JSON.parse(newValue);
        return accumulator;
      },
      {},
    );
  }

  #getScopeMap(
    scopeIds: string[],
    targetElementId: string,
    parentScopeIds: {
      [elementId: string]: string;
    },
  ): ScopeMap {
    let scopeMap: ScopeMap = {};
    for (const [elementId, parentScopeId] of Object.entries(parentScopeIds)) {
      scopeMap[parentScopeId] = elementId;
    }
    for (const scopeId of scopeIds) {
      scopeMap[scopeId] = targetElementId;
    }

    return scopeMap;
  }

  #getScopeMapForModification(
    modification: FlowNodeModificationPayload,
  ): ScopeMap {
    switch (modification.operation) {
      case 'ADD_TOKEN': {
        return this.#getScopeMap(
          [modification.scopeId],
          modification.flowNode.id,
          modification.parentScopeIds,
        );
      }
      case 'MOVE_TOKEN': {
        return this.#getScopeMap(
          modification.scopeIds,
          modification.targetFlowNode.id,
          modification.parentScopeIds,
        );
      }
      default: {
        return {};
      }
    }
  }

  #getVariableInstructionsForScopeMap(
    scopeMap: ScopeMap,
  ): VariableInstruction[] {
    let instructions: VariableInstruction[] = [];
    for (const [scopeId, elementId] of Object.entries(scopeMap)) {
      const variables = this.#getVariablesForScope(scopeId);
      if (variables !== undefined) {
        instructions.push({variables, scopeId: elementId});
      }
    }
    return instructions;
  }

  #generateModificationInstructions(): ModifyProcessInstanceRequestBody {
    let activateInstructions: ActivateInstruction[] = [];
    let moveInstructions: MoveInstruction[] = [];
    let terminateInstructions: TerminateInstruction[] = [];

    for (const modification of this.flowNodeModifications) {
      switch (modification.operation) {
        case 'CANCEL_TOKEN': {
          const instruction: TerminateInstruction =
            modification.flowNodeInstanceKey === undefined
              ? {elementId: modification.flowNode.id}
              : {elementInstanceKey: modification.flowNodeInstanceKey};

          terminateInstructions.push(instruction);
          break;
        }
        case 'ADD_TOKEN': {
          const scopeMap = this.#getScopeMapForModification(modification);

          const instruction: ActivateInstruction = {
            elementId: modification.flowNode.id,
            ancestorElementInstanceKey:
              modification.ancestorElement?.instanceKey,
            variableInstructions:
              this.#getVariableInstructionsForScopeMap(scopeMap),
          };

          activateInstructions.push(instruction);
          break;
        }
        case 'MOVE_TOKEN': {
          const scopeMap = this.#getScopeMapForModification(modification);

          const instruction: MoveInstruction = {
            sourceElementInstruction: modification.flowNodeInstanceKey
              ? {
                  sourceType: 'byKey',
                  sourceElementInstanceKey: modification.flowNodeInstanceKey,
                }
              : {
                  sourceType: 'byId',
                  sourceElementId: modification.flowNode.id,
                },
            targetElementId: modification.targetFlowNode.id,
            ancestorScopeInstruction: modification.ancestorScopeType
              ? {ancestorScopeType: modification.ancestorScopeType}
              : undefined,
            variableInstructions:
              this.#getVariableInstructionsForScopeMap(scopeMap),
          };

          moveInstructions.push(instruction);
          break;
        }
      }
    }

    return {activateInstructions, moveInstructions, terminateInstructions};
  }

  #attachRootVariableModifications(
    processInstanceKey: string,
    instructions: ModifyProcessInstanceRequestBody,
  ): boolean {
    const rootVariables = this.#getVariablesForScope(processInstanceKey);
    if (rootVariables === undefined) {
      return true;
    }

    const hostInstruction =
      instructions.activateInstructions?.at(0) ??
      instructions.moveInstructions?.at(0);
    if (hostInstruction === undefined) {
      return false;
    }

    hostInstruction.variableInstructions ??= [];
    hostInstruction.variableInstructions.push({
      variables: rootVariables,
    });
    return true;
  }

  applyModifications = async ({
    processInstanceId,
    onSuccess,
    onError,
  }: {
    processInstanceId: string;
    onSuccess: () => void;
    onError: (statusCode: number) => void;
  }) => {
    this.startApplyingModifications();

    const instructions = this.#generateModificationInstructions();
    const isAttachRootVarsSuccessful = this.#attachRootVariableModifications(
      processInstanceId,
      instructions,
    );

    if (!isAttachRootVarsSuccessful) {
      logger.error(
        'Failed to attach root variable modifications. No suitable host instruction found.',
      );
      onError(400);
      this.reset();
      return;
    }

    const response = await modifyProcessInstance(
      processInstanceId,
      instructions,
    ).catch(() => null);

    if (response?.ok) {
      onSuccess();
    } else {
      logger.error('Failed to modify Process Instance');
      onError(response?.status ?? 0);
    }

    this.reset();
  };

  hasOrphanedVariableModifications = (processInstanceKey: string): boolean => {
    const variableModifications = this.variableModifications;
    if (variableModifications.length === 0) {
      return false;
    }

    let allPendingScopeIds = new Set<string>();
    let hasAddOrMoveToken = false;

    for (const modification of this.flowNodeModifications) {
      const operation = modification.operation;
      if (operation !== 'ADD_TOKEN' && operation !== 'MOVE_TOKEN') {
        continue;
      }

      hasAddOrMoveToken = true;
      const scopeMap = this.#getScopeMapForModification(modification);
      for (const id of Object.keys(scopeMap)) {
        allPendingScopeIds.add(id);
      }
    }

    return variableModifications.some((modification) => {
      // Root variable modification cannot be applied without an ADD/MOVE token modification
      if (modification.scopeId === processInstanceKey) {
        return !hasAddOrMoveToken;
      }

      return !allPendingScopeIds.has(modification.scopeId);
    });
  };

  getParentScopeId = (flowNodeId: string) => {
    const parentScope = this.flowNodeModifications.find(
      (modification) =>
        modification.operation !== 'CANCEL_TOKEN' &&
        modification.parentScopeIds[flowNodeId] !== undefined,
    );

    if (parentScope !== undefined && 'parentScopeIds' in parentScope) {
      return parentScope.parentScopeIds[flowNodeId];
    }
  };

  addCancelModification = ({
    flowNodeId,
    flowNodeInstanceKey,
    affectedTokenCount,
    visibleAffectedTokenCount,
    businessObjects,
  }: {
    flowNodeId: string;
    flowNodeInstanceKey?: string;
    affectedTokenCount: number;
    visibleAffectedTokenCount: number;
    businessObjects: BusinessObjects;
  }) => {
    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'CANCEL_TOKEN',
        flowNode: {
          id: flowNodeId,
          name: getFlowNodeName({businessObjects, flowNodeId}),
        },
        flowNodeInstanceKey,
        affectedTokenCount,
        visibleAffectedTokenCount,
      },
    });
  };

  cancelToken = (
    flowNodeId: string,
    flowNodeInstanceKey: string,
    businessObjects: BusinessObjects,
  ) => {
    this.addCancelModification({
      flowNodeId,
      flowNodeInstanceKey,
      affectedTokenCount: 1,
      visibleAffectedTokenCount: 1,
      businessObjects,
    });
  };

  addMoveModification = ({
    sourceFlowNodeId,
    sourceFlowNodeInstanceKey,
    targetFlowNodeId,
    newScopeCount,
    affectedTokenCount,
    visibleAffectedTokenCount,
    businessObjects,
    bpmnProcessId,
    ancestorScopeType,
  }: {
    sourceFlowNodeId: string;
    sourceFlowNodeInstanceKey?: string;
    targetFlowNodeId: string;
    newScopeCount: number;
    affectedTokenCount: number;
    visibleAffectedTokenCount: number;
    businessObjects: BusinessObjects;
    bpmnProcessId?: string;
    ancestorScopeType?: AncestorScopeType;
  }) => {
    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'MOVE_TOKEN',
        flowNode: {
          id: sourceFlowNodeId,
          name: getFlowNodeName({
            businessObjects,
            flowNodeId: sourceFlowNodeId,
          }),
        },
        flowNodeInstanceKey: sourceFlowNodeInstanceKey,
        targetFlowNode: {
          id: targetFlowNodeId,
          name: getFlowNodeName({
            businessObjects,
            flowNodeId: targetFlowNodeId,
          }),
        },
        affectedTokenCount,
        visibleAffectedTokenCount,
        scopeIds: Array.from({
          length: newScopeCount,
        }).map(() => generateUniqueID()),
        parentScopeIds: generateParentScopeIds(
          businessObjects,
          targetFlowNodeId,
          bpmnProcessId,
        ),
        ancestorScopeType,
      },
    });
  };

  reset = () => {
    this.state = {...DEFAULT_STATE};
    window.clearTimeout(this.modificationsLoadingTimeout);
  };
}

export type {FlowNodeModification, AncestorScopeType};
export const modificationsStore = new Modifications();
export {EMPTY_MODIFICATION};
