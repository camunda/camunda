/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useContext} from 'react';
import {useSelector} from '@xstate/react';
import type {ProcessDefinition} from '@camunda/camunda-api-zod-schemas/8.10';
import {StartProcessContext} from './startProcessContext';
import {deriveStartProcessStatus} from './startProcessMachine';

function useStartProcess() {
	const actorRef = useContext(StartProcessContext);

	if (actorRef === null) {
		throw new Error('useStartProcess must be used within StartProcessProvider');
	}

	const status = useSelector(actorRef, deriveStartProcessStatus);
	const selectedProcessDefinitionKey = useSelector(
		actorRef,
		(snapshot) => snapshot.context.selectedProcess?.processDefinitionKey ?? null,
	);
	const startProcess = useCallback(
		(process: ProcessDefinition, variables?: Record<string, unknown>) => {
			actorRef.send({type: 'process.start', process, variables});
		},
		[actorRef],
	);
	return {
		status,
		selectedProcessDefinitionKey,
		isBusy: status !== 'inactive',
		startProcess,
	};
}

export {useStartProcess};
