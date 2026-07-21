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
import {parseDiagramXML} from '#/operate/shared/utils/bpmn';
import {getBusinessObjects, getFlowNodes} from '#/operate/shared/utils/elements';

function useDiagramXml(processDefinitionKey?: string) {
	return useQuery({
		queryKey: ['processDefinitionDiagram', processDefinitionKey] as const,
		queryFn:
			processDefinitionKey === undefined
				? skipToken
				: async () => {
						const {response, error} = await request(endpoints.getProcessDefinitionXml({processDefinitionKey}));
						if (error !== null) {
							throw error;
						}

						const xml = await response.text();
						const diagramModel = await parseDiagramXML(xml);

						return {
							xml,
							selectableElements: getFlowNodes(diagramModel.elementsById).map((element) => element.id),
							businessObjects: getBusinessObjects(diagramModel.elementsById),
						};
					},
		staleTime: 'static',
	});
}

export {useDiagramXml};
