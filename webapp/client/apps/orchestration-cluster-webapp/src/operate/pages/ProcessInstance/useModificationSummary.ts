/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useLayoutEffect, useRef} from 'react';
import {useTranslation} from 'react-i18next';
import {useQueryClient} from '@tanstack/react-query';
import type {CallHierarchy} from '@camunda/camunda-api-zod-schemas/8.11';
import {endpoints} from '#/shared/http/endpoints';
import {request} from '#/shared/http/request';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {useCallHierarchy} from '#/operate/shared/Operations/Operations.queries';
import {useDiagramXml} from '#/operate/pages/Processes/useDiagramXml';
import {useProcessInstancePage} from './useProcessInstancePage';
import {useProcessInstanceElementSelection} from './useProcessInstanceElementSelection';
import {useProcessInstanceModification} from './useProcessInstanceModification';
import {useInstanceStatistics} from './instanceDiagram.queries';
import {variableQueryKeys} from './variables.queries';
import {buildModificationInstructions} from './modifications/modificationInstructions';
import {generateId} from './modifications/modificationOperations';
import {
	getElementModifications,
	getModificationsByElement,
	getVariableModifications,
	hasOrphanedVariableModifications,
	willAllElementsBeCanceled,
} from './modifications/modificationSelectors';
import {
	getRunningInstancesByElement,
	hasActivationInCanceledScope,
	withoutMovesFromCanceledScopes,
} from './modifications/modificationTargets';

const OPERATION_LABEL_KEYS = {
	ADD_TOKEN: 'operate.processInstance.modification.add',
	CANCEL_TOKEN: 'operate.processInstance.modification.cancel',
	MOVE_TOKEN: 'operate.processInstance.modification.move',
	ADD_VARIABLE: 'operate.processInstance.modification.add',
	EDIT_VARIABLE: 'operate.processInstance.modification.edit',
} as const;

const HTTP_STATUS_FORBIDDEN = 403;

function beautifyJSON(value: string) {
	try {
		return JSON.stringify(JSON.parse(value), null, '\t');
	} catch {
		return value;
	}
}

function getCallHierarchyLabel({processDefinitionName, processInstanceKey}: CallHierarchy) {
	return processDefinitionName === undefined ? processInstanceKey : `${processDefinitionName} - ${processInstanceKey}`;
}

function useChildInstanceErrorProcesses() {
	const {processInstanceId, processInstance} = useProcessInstancePage();
	const {data: callHierarchy} = useCallHierarchy(processInstanceId, {enabled: true});
	const findInHierarchy = (processInstanceKey: string | null) =>
		processInstanceKey === null
			? undefined
			: callHierarchy?.find((entry) => entry.processInstanceKey === processInstanceKey);

	return {
		parentProcess: findInHierarchy(processInstance.parentProcessInstanceKey),
		rootProcess: findInHierarchy(processInstance.rootProcessInstanceKey),
	};
}

function useScrollbarAlignment() {
	const elementTableRef = useRef<HTMLDivElement>(null);
	const variableTableRef = useRef<HTMLDivElement>(null);

	useLayoutEffect(() => {
		const elementTable = elementTableRef.current;
		const variableTable = variableTableRef.current;
		if (elementTable === null || variableTable === null) {
			return;
		}
		const variableScrollbarWidth = variableTable.offsetWidth - variableTable.scrollWidth;
		const elementScrollbarWidth = elementTable.offsetWidth - elementTable.scrollWidth;
		const isAligned = variableScrollbarWidth === elementScrollbarWidth;
		elementTable.style.paddingRight = isAligned ? '0px' : `${variableScrollbarWidth}px`;
		variableTable.style.paddingRight = isAligned ? '0px' : `${elementScrollbarWidth}px`;
	});

	return {elementTableRef, variableTableRef};
}

function useModificationSummary(onClose: () => void) {
	const {t} = useTranslation();
	const queryClient = useQueryClient();
	const {processInstanceId, processInstance} = useProcessInstancePage();
	const {clearSelection} = useProcessInstanceElementSelection();
	const {state, dispatch} = useProcessInstanceModification();
	const {data: diagram} = useDiagramXml(processInstance.processDefinitionKey);
	const statistics = useInstanceStatistics(processInstance, diagram !== undefined && diagram.xml !== '');
	const runningInstancesByElement = getRunningInstancesByElement(statistics.data, diagram?.businessObjects);
	const modificationsByElement = getModificationsByElement(state, {
		businessObjects: diagram?.businessObjects,
		totalRunningInstancesByElement: runningInstancesByElement,
		totalRunningInstancesVisibleByElement: runningInstancesByElement,
	});
	const elementModifications = getElementModifications(state);
	const variableModifications = getVariableModifications(state);
	const effectiveState = withoutMovesFromCanceledScopes(state, diagram?.businessObjects, modificationsByElement);
	const isProcessCanceled = willAllElementsBeCanceled(effectiveState, statistics.data, modificationsByElement);
	const hasParentProcess = Boolean(processInstance.parentProcessInstanceKey);
	const areModificationsInvalid = isProcessCanceled && hasParentProcess;
	const hasOrphanedVariables = hasOrphanedVariableModifications(state, processInstanceId);
	const hasCanceledScopeConflict = hasActivationInCanceledScope(
		state,
		diagram?.businessObjects,
		modificationsByElement,
	);
	const hasCanceledMoveSource = effectiveState !== state;
	const isApplyDisabled =
		areModificationsInvalid ||
		hasOrphanedVariables ||
		hasCanceledScopeConflict ||
		hasCanceledMoveSource ||
		state.modifications.length === 0;
	const {elementTableRef, variableTableRef} = useScrollbarAlignment();

	const showError = (statusCode?: number) =>
		notificationsStore.displayNotification({
			kind: 'error',
			title: t('operate.processInstance.modification.failed'),
			subtitle: t(
				statusCode === HTTP_STATUS_FORBIDDEN
					? 'operate.processInstance.modification.failedNoPermission'
					: 'operate.processInstance.modification.failedGeneric',
			),
			isDismissable: true,
		});

	const applyModifications = async () => {
		onClose();
		clearSelection();
		const requestId = generateId();
		const result = buildModificationInstructions(state, processInstanceId);
		dispatch({type: 'startApplyingModifications', requestId});

		if (result.status === 'missing-root-variable-host') {
			showError();
			dispatch({type: 'finishApplyingModifications', requestId});
			return;
		}

		const {error} = await request(endpoints.modifyProcessInstance(processInstanceId, result.instructions));

		if (error === null) {
			void queryClient.invalidateQueries({queryKey: ['elementInstanceWaitStates']});
			void queryClient.invalidateQueries({queryKey: ['instanceHistory', processInstanceId]});
			void queryClient.invalidateQueries({queryKey: variableQueryKeys.instanceVariables(processInstanceId)});
			void queryClient.invalidateQueries({queryKey: variableQueryKeys.variableValues()});
			notificationsStore.displayNotification({
				kind: 'success',
				title: t('operate.processInstance.modification.applied'),
				isDismissable: true,
			});
		} else {
			showError(error.response?.status);
		}

		dispatch({type: 'finishApplyingModifications', requestId});
	};

	return {
		elementModifications,
		variableModifications,
		modificationsByElement,
		isProcessCanceled,
		hasParentProcess,
		areModificationsInvalid,
		hasOrphanedVariables,
		hasCanceledScopeConflict,
		hasCanceledMoveSource,
		isApplyDisabled,
		elementTableRef,
		variableTableRef,
		applyModifications,
		removeElementModification: (modification: (typeof elementModifications)[number]) =>
			dispatch({type: 'removeElementModification', modification}),
		removeVariableModification: ({
			scopeId,
			id,
			operation,
		}: Pick<(typeof variableModifications)[number], 'scopeId' | 'id' | 'operation'>) =>
			dispatch({type: 'removeVariableModification', scopeId, id, operation, source: 'summaryModal'}),
	};
}

export {
	beautifyJSON,
	getCallHierarchyLabel,
	OPERATION_LABEL_KEYS,
	useChildInstanceErrorProcesses,
	useModificationSummary,
};
