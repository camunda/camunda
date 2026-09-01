/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo} from 'react';
import type {BatchOperationType, ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.10';
import {Operations} from '#/operate/shared/Operations/Operations';
import type {OperationConfig} from '#/operate/shared/Operations/types';
import {useProcessInstanceOperations} from './useProcessInstanceOperations';

type Props = {
	processInstance: Pick<ProcessInstance, 'processInstanceKey' | 'state' | 'hasIncident'>;
	activeOperations: BatchOperationType[];
};

const InstanceOperations: React.FC<Props> = ({processInstance, activeOperations}) => {
	const {processInstanceKey, state, hasIncident} = processInstance;
	const {pendingOperation, resolveIncidents, cancel, remove} = useProcessInstanceOperations(processInstanceKey);

	const isActive = state === 'ACTIVE';
	const isSuspended = state === 'SUSPENDED';
	const isFinished = state === 'COMPLETED' || state === 'TERMINATED';
	// A row is busy while its own command is in flight or while the server still reports an
	// operation running against this instance from elsewhere (a batch operation, another tab).
	const isLoading = pendingOperation !== null || activeOperations.length > 0;

	const operations = useMemo<OperationConfig[]>(() => {
		const isBusyWith = (operationType: BatchOperationType) =>
			pendingOperation === operationType || activeOperations.includes(operationType);

		const configs: OperationConfig[] = [];

		if (isActive && hasIncident) {
			configs.push({
				type: 'RESOLVE_INCIDENT',
				onExecute: () => void resolveIncidents(),
				disabled: isBusyWith('RESOLVE_INCIDENT'),
			});
		}

		// Legacy offers cancel on a suspended instance too. Suspend/resume themselves wait on
		// #61094, which brings both the shared renderers and the filter that surfaces these rows.
		if (isActive || isSuspended) {
			configs.push({
				type: 'CANCEL_PROCESS_INSTANCE',
				onExecute: () => void cancel(),
				disabled: isBusyWith('CANCEL_PROCESS_INSTANCE'),
			});
		}

		if (isFinished) {
			configs.push({
				type: 'DELETE_PROCESS_INSTANCE',
				onExecute: () => void remove(),
				disabled: isBusyWith('DELETE_PROCESS_INSTANCE'),
			});
		}

		return configs;
	}, [
		activeOperations,
		cancel,
		hasIncident,
		isActive,
		isFinished,
		isSuspended,
		pendingOperation,
		remove,
		resolveIncidents,
	]);

	return <Operations operations={operations} processInstanceKey={processInstanceKey} isLoading={isLoading} />;
};

export {InstanceOperations};
