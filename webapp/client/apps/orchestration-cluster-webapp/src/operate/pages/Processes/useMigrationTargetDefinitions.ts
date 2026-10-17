/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo} from 'react';
import {useQuery} from '@tanstack/react-query';
import type {ProcessDefinition} from '@camunda/camunda-api-zod-schemas/8.11';
import {operationsLogDefinitionsQuery} from '#/operate/shared/queries/processDefinitions.queries';
import {getProcessDefinitionName} from './getProcessDefinitionName';

const STALE_TIME = 60_000;

// The engine rejects migrations to a definition that is being deleted, so only ACTIVE ones are offered.

function sortByVersionDesc(definitions: ProcessDefinition[]) {
	return [...definitions].sort((a, b) => b.version - a.version);
}

function useInitialMigrationTarget(source: ProcessDefinition) {
	return useQuery({
		...operationsLogDefinitionsQuery({
			processDefinitionId: source.processDefinitionId,
			tenantId: source.tenantId,
			state: 'ACTIVE',
		}),
		staleTime: STALE_TIME,
		select: (definitions) =>
			sortByVersionDesc(definitions).find(
				({processDefinitionKey}) => processDefinitionKey !== source.processDefinitionKey,
			),
	});
}

function useAvailableMigrationTargets(source: ProcessDefinition) {
	const {data: initialTarget} = useInitialMigrationTarget(source);
	const {data: otherDefinitions} = useQuery({
		...operationsLogDefinitionsQuery({
			isLatestVersion: true,
			tenantId: source.tenantId,
			processDefinitionId: {$neq: source.processDefinitionId},
			state: 'ACTIVE',
		}),
		staleTime: STALE_TIME,
	});

	return useMemo(() => {
		if (otherDefinitions === undefined) {
			return initialTarget ? [initialTarget] : [];
		}
		return otherDefinitions
			.concat(initialTarget ?? [])
			.sort((a, b) => getProcessDefinitionName(a).localeCompare(getProcessDefinitionName(b)));
	}, [initialTarget, otherDefinitions]);
}

function useMigrationTargetVersions(source: ProcessDefinition, target: ProcessDefinition | null) {
	return useQuery({
		...operationsLogDefinitionsQuery({
			processDefinitionId: target?.processDefinitionId,
			tenantId: target?.tenantId,
			state: 'ACTIVE',
		}),
		enabled: target !== null,
		staleTime: STALE_TIME,
		select: (definitions) =>
			sortByVersionDesc(definitions).filter(
				({processDefinitionKey}) => processDefinitionKey !== source.processDefinitionKey,
			),
	});
}

export {useInitialMigrationTarget, useAvailableMigrationTargets, useMigrationTargetVersions};
