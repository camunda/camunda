/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {HttpResponse, http} from 'msw';
import {endpoints, queryDecisionDefinitionsRequestBodySchema} from '@camunda/camunda-api-zod-schemas/8.10';

function mockQueryDecisionDefinitionsEndpointByFilter({
	unfilteredResponse,
	filteredResponse,
	versionsResponses = [filteredResponse],
}: {
	unfilteredResponse: Response;
	filteredResponse: Response;
	versionsResponses?: Response[];
}) {
	let versionResponseIndex = 0;

	return http.post(endpoints.queryDecisionDefinitions.getUrl(), async ({request}) => {
		const result = queryDecisionDefinitionsRequestBodySchema.safeParse(await request.json());
		if (!result.success) {
			return HttpResponse.json({}, {status: 400});
		}

		if (result.data.filter?.decisionDefinitionId === undefined) {
			return unfilteredResponse.clone();
		}
		if (result.data.filter.version !== undefined) {
			return filteredResponse.clone();
		}

		const response = versionsResponses[Math.min(versionResponseIndex, versionsResponses.length - 1)];
		versionResponseIndex += 1;
		return (response ?? filteredResponse).clone();
	});
}

export {mockQueryDecisionDefinitionsEndpointByFilter};
