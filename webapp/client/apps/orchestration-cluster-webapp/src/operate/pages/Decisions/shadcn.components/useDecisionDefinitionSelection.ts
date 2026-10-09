/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo} from 'react';
import {useQuery} from '@tanstack/react-query';
import {isSpecificTenant} from '#/operate/shared/utils/isSpecificTenant';
import {decisionDefinitionSelectionOptions} from '../decisions.queries';
import type {DecisionDefinitionSelection} from './DecisionPanel';

type Params = {
	decisionDefinitionId?: string;
	decisionDefinitionVersion?: number;
	tenantId?: string;
};

function useDecisionDefinitionSelection({decisionDefinitionId, decisionDefinitionVersion, tenantId}: Params) {
	const specificTenantId = isSpecificTenant(tenantId) ? tenantId : undefined;
	const query = useQuery({
		...decisionDefinitionSelectionOptions({
			decisionDefinitionId,
			decisionDefinitionVersion,
			tenantId: specificTenantId,
		}),
		enabled: decisionDefinitionId !== undefined,
	});

	const selection = useMemo<DecisionDefinitionSelection>(() => {
		if (!decisionDefinitionId || query.status !== 'success') {
			return {kind: 'no-match'};
		}

		const matches = query.data.items.filter(
			(definition) =>
				definition.decisionDefinitionId === decisionDefinitionId &&
				(decisionDefinitionVersion === undefined || definition.version === decisionDefinitionVersion) &&
				(specificTenantId === undefined || definition.tenantId === specificTenantId),
		);

		if (decisionDefinitionVersion === undefined) {
			const first = matches[0];
			return first === undefined
				? {kind: 'no-match'}
				: {kind: 'all-versions', definition: {name: first.name, decisionDefinitionId: first.decisionDefinitionId}};
		}

		const definition = matches[0];
		if (definition === undefined) {
			return {kind: 'no-match'};
		}
		if (new Set(matches.map(({tenantId: definitionTenantId}) => definitionTenantId)).size > 1) {
			return {
				kind: 'multiple-tenants',
				definition: {name: definition.name, decisionDefinitionId: definition.decisionDefinitionId},
			};
		}
		return {kind: 'single-version', definition};
	}, [decisionDefinitionId, decisionDefinitionVersion, query.data, query.status, specificTenantId]);

	return {
		decisionDefinitionSelection: selection,
		isDefinitionSelectionLoading:
			decisionDefinitionId !== undefined && (query.isPending || query.fetchStatus !== 'idle'),
		isDefinitionSelectionError: query.isError,
	};
}

export {useDecisionDefinitionSelection};
