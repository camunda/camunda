/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {skipToken, useQuery} from '@tanstack/react-query';
import {request} from '#/shared/http/request';
import {endpoints} from '#/shared/http/endpoints';

function useDecisionDefinitionXml(decisionDefinitionKey?: string) {
	return useQuery({
		queryKey: ['decisionDefinitionXml', decisionDefinitionKey] as const,
		queryFn:
			decisionDefinitionKey === undefined
				? skipToken
				: async () => {
						const {response, error} = await request(endpoints.getDecisionDefinitionXml({decisionDefinitionKey}));
						if (error !== null) {
							throw error;
						}

						return response.text();
					},
		staleTime: 'static',
	});
}

export {useDecisionDefinitionXml};
