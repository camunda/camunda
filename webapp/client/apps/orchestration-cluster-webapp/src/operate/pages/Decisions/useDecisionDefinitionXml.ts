/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {queryOptions, useSuspenseQuery} from '@tanstack/react-query';
import {request} from '#/shared/http/request';
import {mapQueryError} from '#/shared/http/mapQueryError';
import {endpoints} from '#/shared/http/endpoints';

async function fetchDecisionDefinitionXml(decisionDefinitionKey: string) {
	const {response, error} = await request(endpoints.getDecisionDefinitionXml({decisionDefinitionKey}));
	if (error !== null) {
		throw mapQueryError(error);
	}

	return response.text();
}

function decisionDefinitionXmlQuery(decisionDefinitionKey: string) {
	return queryOptions({
		queryKey: ['decisionDefinitionXml', decisionDefinitionKey] as const,
		queryFn: () => fetchDecisionDefinitionXml(decisionDefinitionKey),
		staleTime: 'static',
	});
}

function useDecisionDefinitionXml(decisionDefinitionKey: string) {
	return useSuspenseQuery(decisionDefinitionXmlQuery(decisionDefinitionKey));
}

export {useDecisionDefinitionXml, fetchDecisionDefinitionXml};
