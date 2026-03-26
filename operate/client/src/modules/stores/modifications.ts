/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {makeAutoObservable} from 'mobx';
import {generateUniqueID} from 'modules/utils/generateUniqueID';
import {type ModifyProcessInstanceRequestBody} from '@camunda/camunda-api-zod-schemas/8.10';
import {modifyProcessInstance} from 'modules/api/v2/processInstances/modifyProcessInstance';
import {logger} from 'modules/logger';
import {tracking} from 'modules/tracking';
import {getElementName} from 'modules/utils/elements';
import {getElementsInBetween} from 'modules/utils/processInstanceDetailsDiagram';
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

type ElementModificationPayload =
  | {
      operation: 'ADD_TOKEN';
      scopeId: string;
      element: {id: string; name: string};
      affectedTokenCount: number;
      visibleAffectedTokenCount: number;
      ancestorElement?: {
        instanceKey: string;
        elementId: string;
      };
      parentScopeIds: {
        [elementId: string]: string;
      };
    }
  | {
      operation: 'CANCEL_TOKEN';
      element: {id: string; name: string};
      elementInstanceKey?: string;
      affectedTokenCount: number;
      visibleAffectedTokenCount: number;
    }
  | {
      operation: 'MOVE_TOKEN';
      element: {id: string; name: string};
      elementInstanceKey?: string;
      affectedTokenCount: number;
      visibleAffectedTokenCount: number;
      targetElement: {id: string; name: string};
      scopeIds: string[];
      parentScopeIds: {
        [elementId: string]: string;
      };
      ancestorScopeType?: AncestorScopeType;
    };

type VariableModificationPayload = {
  operation: 'ADD_VARIABLE' | 'EDIT_VARIABLE';
  id: string;
  scopeId: string;
  elementName: string;
  name: string;
  oldValue?: string;
  newValue: string;
};

type ElementModification = {
  type: 'token';
  payload: ElementModificationPayload;
};

type VariableModification = {
  type: 'variable';
  payload: VariableModificationPayload;
};

type Modification = ElementModification | VariableModification;
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
  sourceElementIdForMoveOperation: string | null;
  sourceElementInstanceKeyForMoveOperation: string | null;
  sourceElementIdForAddOperation: string | null;
};

const DEFAULT_STATE: State = {
  status: 'disabled',
  modifications: [],
  sourceElementIdForMoveOperation: null,
  sourceElementInstanceKeyForMoveOperation: null,
  sourceElementIdForAddOperation: null,
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
    sourceElementId: string,
    sourceElementInstanceKey?: string,
  ) => {
    this.setStatus('moving-token');
    this.setSourceElementIdForMoveOperation(sourceElementId);
    this.setSourceElementInstanceKeyForMoveOperation(
      sourceElementInstanceKey ?? null,
    );
  };

  startAddingToken = (sourceElementId: string) => {
    this.setStatus('adding-token');
    this.state.sourceElementIdForAddOperation = sourceElementId;
  };

  generateScopeIdsInBetween = (
    targetElementId: string,
    ancestorElementId: string,
    businessObjects: BusinessObjects,
  ) => {
    const elementsInBetween = getElementsInBetween(
      businessObjects,
      targetElementId,
      ancestorElementId,
    );

    return elementsInBetween.reduce<{[elementId: string]: string}>(
      (elementScopes, elementId) => {
        elementScopes[elementId] = generateUniqueID();
        return elementScopes;
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
      this.state.sourceElementIdForAddOperation !== null
    ) {
      this.addModification({
        type: 'token',
        payload: {
          operation: 'ADD_TOKEN',
          scopeId: generateUniqueID(),
          element: {
            id: this.state.sourceElementIdForAddOperation,
            name:
              getElementName({
                businessObjects,
                elementId: this.state.sourceElementIdForAddOperation,
              }) ?? '',
          },
          affectedTokenCount: 1,
          visibleAffectedTokenCount: 1,
          ancestorElement: {
            instanceKey: ancestorElementInstanceKey,
            elementId: ancestorElementId,
          },
          parentScopeIds: this.generateScopeIdsInBetween(
            this.state.sourceElementIdForAddOperation,
            ancestorElementId,
            businessObjects,
          ),
        },
      });
    }

    this.setStatus('enabled');
    this.state.sourceElementIdForAddOperation = null;
  };

  setStatus = (status: State['status']) => {
    this.state.status = status;
  };

  setSourceElementIdForMoveOperation = (sourceElementId: string | null) => {
    this.state.sourceElementIdForMoveOperation = sourceElementId;
  };

  setSourceElementInstanceKeyForMoveOperation = (
    sourceElementInstanceKey: string | null,
  ) => {
    this.state.sourceElementInstanceKeyForMoveOperation =
      sourceElementInstanceKey;
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

  removeElementModification = (
    elementModification: ElementModificationPayload,
  ) => {
    if (elementModification.operation === 'ADD_TOKEN') {
      this.state.modifications = this.state.modifications.filter(
        ({type, payload}) =>
          !(
            type === 'token' &&
            payload.element.id === elementModification.element.id &&
            payload.operation === elementModification.operation &&
            payload.scopeId === elementModification.scopeId
          ),
      );
    } else {
      this.state.modifications = this.state.modifications.filter(
        ({type, payload}) =>
          !(
            type === 'token' &&
            payload.element.id === elementModification.element.id &&
            payload.operation === elementModification.operation &&
            payload.elementInstanceKey ===
              elementModification.elementInstanceKey
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
      this.state.sourceElementInstanceKeyForMoveOperation === null
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

  get elementModifications() {
    function isElementModification(
      modification: Modification,
    ): modification is ElementModification {
      const {type} = modification;

      return type === 'token';
    }

    return this.state.modifications
      .filter(isElementModification)
      .map(({payload}) => payload);
  }

  getLastVariableModification = (
    elementInstanceId: string | null,
    id: string,
    operation: 'ADD_VARIABLE' | 'EDIT_VARIABLE',
  ) => {
    return this.variableModifications.find(
      (modification) =>
        modification.operation === operation &&
        modification.scopeId === elementInstanceId &&
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
    modification: ElementModificationPayload,
  ): ScopeMap {
    switch (modification.operation) {
      case 'ADD_TOKEN': {
        return this.#getScopeMap(
          [modification.scopeId],
          modification.element.id,
          modification.parentScopeIds,
        );
      }
      case 'MOVE_TOKEN': {
        return this.#getScopeMap(
          modification.scopeIds,
          modification.targetElement.id,
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

    for (const modification of this.elementModifications) {
      switch (modification.operation) {
        case 'CANCEL_TOKEN': {
          const instruction: TerminateInstruction =
            modification.elementInstanceKey === undefined
              ? {elementId: modification.element.id}
              : {elementInstanceKey: modification.elementInstanceKey};

          terminateInstructions.push(instruction);
          break;
        }
        case 'ADD_TOKEN': {
          const scopeMap = this.#getScopeMapForModification(modification);

          const instruction: ActivateInstruction = {
            elementId: modification.element.id,
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
            sourceElementInstruction: modification.elementInstanceKey
              ? {
                  sourceType: 'byKey',
                  sourceElementInstanceKey: modification.elementInstanceKey,
                }
              : {
                  sourceType: 'byId',
                  sourceElementId: modification.element.id,
                },
            targetElementId: modification.targetElement.id,
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

    for (const modification of this.elementModifications) {
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

  getParentScopeId = (elementId: string) => {
    const parentScope = this.elementModifications.find(
      (modification) =>
        modification.operation !== 'CANCEL_TOKEN' &&
        modification.parentScopeIds[elementId] !== undefined,
    );

    if (parentScope !== undefined && 'parentScopeIds' in parentScope) {
      return parentScope.parentScopeIds[elementId];
    }
  };

  addCancelModification = ({
    elementId,
    elementInstanceKey,
    affectedTokenCount,
    visibleAffectedTokenCount,
    businessObjects,
  }: {
    elementId: string;
    elementInstanceKey?: string;
    affectedTokenCount: number;
    visibleAffectedTokenCount: number;
    businessObjects: BusinessObjects;
  }) => {
    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'CANCEL_TOKEN',
        element: {
          id: elementId,
          name: getElementName({businessObjects, elementId: elementId}),
        },
        elementInstanceKey,
        affectedTokenCount,
        visibleAffectedTokenCount,
      },
    });
  };

  cancelToken = (
    elementId: string,
    elementInstanceKey: string,
    businessObjects: BusinessObjects,
  ) => {
    this.addCancelModification({
      elementId,
      elementInstanceKey,
      affectedTokenCount: 1,
      visibleAffectedTokenCount: 1,
      businessObjects,
    });
  };

  addMoveModification = ({
    sourceElementId,
    sourceElementInstanceKey,
    targetElementId,
    newScopeCount,
    affectedTokenCount,
    visibleAffectedTokenCount,
    businessObjects,
    bpmnProcessId,
    ancestorScopeType,
  }: {
    sourceElementId: string;
    sourceElementInstanceKey?: string;
    targetElementId: string;
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
        element: {
          id: sourceElementId,
          name: getElementName({
            businessObjects,
            elementId: sourceElementId,
          }),
        },
        elementInstanceKey: sourceElementInstanceKey,
        targetElement: {
          id: targetElementId,
          name: getElementName({
            businessObjects,
            elementId: targetElementId,
          }),
        },
        affectedTokenCount,
        visibleAffectedTokenCount,
        scopeIds: Array.from({
          length: newScopeCount,
        }).map(() => generateUniqueID()),
        parentScopeIds: generateParentScopeIds(
          businessObjects,
          targetElementId,
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

export type {ElementModification, AncestorScopeType};
export const modificationsStore = new Modifications();
export {EMPTY_MODIFICATION};
