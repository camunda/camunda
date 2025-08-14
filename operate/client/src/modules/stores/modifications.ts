/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {makeAutoObservable} from 'mobx';
import {generateUniqueID} from 'modules/utils/generateUniqueID';
import {
  modify,
  type ModificationPayload,
  type FlowNodeVariables,
} from 'modules/api/processInstances/modify';
import {logger} from 'modules/logger';
import {tracking} from 'modules/tracking';
import {getFlowNodeName} from 'modules/utils/flowNodes';
import {getFlowNodesInBetween} from 'modules/utils/processInstanceDetailsDiagram';
import type {BusinessObjects} from 'bpmn-js/lib/NavigatedViewer';
import {generateParentScopeIds} from 'modules/utils/modifications';

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
      ancestorElement?: {
        instanceKey: string;
        flowNodeId: string;
      };
      parentScopeIds: {
        [flowNodeId: string]: string;
      };
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
    makeAutoObservable(this, {
      generateModificationsPayload: false,
      setVariableModificationsForParentScopes: false,
    });
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

  getVariableModificationsPerScope = (scopeId: string) => {
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
  };

  setVariableModificationsForParentScopes = (parentScopeIds: {
    [flowNodeId: string]: string;
  }) => {
    return Object.entries(parentScopeIds).reduce<FlowNodeVariables>(
      (variableModifications, [flowNodeId, scopeId]) => {
        if (scopeId === undefined) {
          return variableModifications;
        }

        const variables = this.getVariableModificationsPerScope(scopeId);

        if (variables === undefined) {
          return variableModifications;
        }

        variableModifications[flowNodeId] = [variables];

        return variableModifications;
      },
      {},
    );
  };

  generateModificationsPayload = () => {
    let variablesForNewScopes: string[] = [];
    const flowNodeModifications = this.flowNodeModifications.reduce<
      ModificationPayload['modifications']
    >((modifications, payload) => {
      const {operation} = payload;

      if (operation === 'ADD_TOKEN') {
        const variablesPerScope = this.getVariableModificationsPerScope(
          payload.scopeId,
        );

        const variablesForParentScopes =
          this.setVariableModificationsForParentScopes(payload.parentScopeIds);

        variablesForNewScopes = variablesForNewScopes.concat([
          payload.scopeId,
          ...Object.values(payload.parentScopeIds),
        ]);

        const allVariables = {...variablesForParentScopes};
        if (variablesPerScope !== undefined) {
          allVariables[payload.flowNode.id] = [variablesPerScope];
        }

        return [
          ...modifications,
          {
            modification: payload.operation,
            toFlowNodeId: payload.flowNode.id,
            ancestorElementInstanceKey: payload.ancestorElement?.instanceKey,
            variables:
              Object.keys(allVariables).length > 0 ? allVariables : undefined,
          },
        ];
      }

      if (operation === 'CANCEL_TOKEN') {
        return [
          ...modifications,
          {
            modification: payload.operation,
            ...(payload.flowNodeInstanceKey === undefined
              ? {fromFlowNodeId: payload.flowNode.id}
              : {fromFlowNodeInstanceKey: payload.flowNodeInstanceKey}),
          },
        ];
      }

      if (operation === 'MOVE_TOKEN') {
        const {scopeIds, operation, flowNode, targetFlowNode, parentScopeIds} =
          payload;

        const variablesForAllTargetScopes = scopeIds.reduce<
          Array<{[key: string]: string}>
        >((allVariables, scopeId) => {
          const variables = this.getVariableModificationsPerScope(scopeId);
          if (variables === undefined) {
            return allVariables;
          }

          variablesForNewScopes.push(scopeId);
          return [...allVariables, variables];
        }, []);

        const variablesForParentScopes =
          this.setVariableModificationsForParentScopes(parentScopeIds);
        variablesForNewScopes = variablesForNewScopes.concat(
          Object.values(parentScopeIds),
        );

        const allVariables = {...variablesForParentScopes};
        if (variablesForAllTargetScopes.length > 0) {
          allVariables[targetFlowNode.id] = variablesForAllTargetScopes;
        }

        return [
          ...modifications,
          {
            modification: operation,
            ...(payload.flowNodeInstanceKey === undefined
              ? {fromFlowNodeId: flowNode.id}
              : {fromFlowNodeInstanceKey: payload.flowNodeInstanceKey}),
            toFlowNodeId: targetFlowNode.id,
            newTokensCount: scopeIds.length,
            variables:
              Object.keys(allVariables).length > 0 ? allVariables : undefined,
          },
        ];
      }

      return modifications;
    }, []);

    const variableModifications = this.variableModifications
      .filter(({scopeId}) => !variablesForNewScopes.includes(scopeId))
      .map(({operation, scopeId, name, newValue}) => {
        return {
          modification: operation,
          scopeKey: scopeId,
          variables: {[name]: JSON.parse(newValue)},
        };
      });

    return [...flowNodeModifications, ...variableModifications];
  };

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

    const response = await modify({
      processInstanceId,
      payload: {modifications: this.generateModificationsPayload()},
    });

    if (response.isSuccess) {
      onSuccess();
    } else {
      logger.error('Failed to modify Process Instance');
      onError(response.statusCode);
    }

    this.reset();
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
  }: {
    sourceFlowNodeId: string;
    sourceFlowNodeInstanceKey?: string;
    targetFlowNodeId: string;
    newScopeCount: number;
    affectedTokenCount: number;
    visibleAffectedTokenCount: number;
    businessObjects: BusinessObjects;
    bpmnProcessId?: string;
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
      },
    });
  };

  reset = () => {
    this.state = {...DEFAULT_STATE};
    window.clearTimeout(this.modificationsLoadingTimeout);
  };
}

export type {FlowNodeModification, FlowNodeModificationPayload};
export const modificationsStore = new Modifications();
export {EMPTY_MODIFICATION};
