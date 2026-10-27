/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {QueryClient} from '@tanstack/react-query';
import {isSpecificTenant} from '#/operate/shared/utils/isSpecificTenant';
import {decisionDefinitionSelectionOptions, decisionDefinitionsOptions} from './decisions.queries';

type LoadDecisionsDataOptions = {
	queryClient: QueryClient;
	decisionDefinitionId?: string;
	decisionDefinitionVersion?: number;
	tenantId?: string;
};

async function loadDecisionsData({
	queryClient,
	decisionDefinitionId,
	decisionDefinitionVersion,
	tenantId,
}: LoadDecisionsDataOptions) {
	const specificTenantId = isSpecificTenant(tenantId) ? tenantId : undefined;
	await queryClient.ensureQueryData(decisionDefinitionsOptions(specificTenantId));

	if (decisionDefinitionId === undefined) {
		return true;
	}

	const selection = await queryClient.fetchQuery({
		...decisionDefinitionSelectionOptions({
			decisionDefinitionId,
			decisionDefinitionVersion,
			tenantId: specificTenantId,
		}),
		staleTime: 0,
	});
	return selection.items.length > 0;
}

export {loadDecisionsData};
