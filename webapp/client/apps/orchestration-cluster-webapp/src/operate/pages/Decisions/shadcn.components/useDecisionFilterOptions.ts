/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo} from 'react';
import {useQuery, useSuspenseQuery} from '@tanstack/react-query';
import {isSpecificTenant} from '#/operate/shared/utils/isSpecificTenant';
import {decisionDefinitionSelectionOptions, decisionDefinitionsOptions} from '../decisions.queries';
import {useDecisionDefinitionVersions} from '../useDecisionDefinitionVersions';

type Params = {
	decisionDefinitionId?: string;
	decisionDefinitionVersion?: number;
	tenantId?: string;
};

type DecisionOption = {value: string; label: string};

function useDecisionFilterOptions({decisionDefinitionId, decisionDefinitionVersion, tenantId}: Params) {
	const specificTenantId = isSpecificTenant(tenantId) ? tenantId : undefined;
	const {data} = useSuspenseQuery(decisionDefinitionsOptions(specificTenantId));
	const selectionQuery = useQuery({
		...decisionDefinitionSelectionOptions({
			decisionDefinitionId,
			decisionDefinitionVersion,
			tenantId: specificTenantId,
		}),
		enabled: decisionDefinitionId !== undefined,
	});
	const versionsQuery = useDecisionDefinitionVersions(decisionDefinitionId, specificTenantId);

	const definitions = useMemo(
		() => [...data.items, ...(selectionQuery.data?.items ?? []), ...(versionsQuery.data ?? [])],
		[data, selectionQuery.data, versionsQuery.data],
	);

	const decisionOptions = useMemo<DecisionOption[]>(() => {
		const options = new Map(
			definitions.map(({decisionDefinitionId: id, name}) => [id, {value: id, label: name ?? id}] as const),
		);
		if (decisionDefinitionId !== undefined && !options.has(decisionDefinitionId)) {
			options.set(decisionDefinitionId, {value: decisionDefinitionId, label: decisionDefinitionId});
		}
		return [...options.values()];
	}, [definitions, decisionDefinitionId]);

	const versions = useMemo<number[]>(() => {
		if (!decisionDefinitionId) {
			return [];
		}
		const versionSet = new Set(
			definitions
				.filter((definition) => definition.decisionDefinitionId === decisionDefinitionId)
				.map(({version}) => version),
		);
		if (decisionDefinitionVersion !== undefined) {
			versionSet.add(decisionDefinitionVersion);
		}
		return [...versionSet].sort((a, b) => b - a);
	}, [definitions, decisionDefinitionId, decisionDefinitionVersion]);

	return {decisionOptions, versions};
}

export {useDecisionFilterOptions};
