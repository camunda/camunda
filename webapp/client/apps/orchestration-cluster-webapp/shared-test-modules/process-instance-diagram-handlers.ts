/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {HttpResponse} from 'msw';
import {createProcessDefinitionStatistic} from './api-mocks/process-definition-statistics';
import {mockGetProcessInstanceSequenceFlowsEndpoint, mockGetProcessInstanceStatisticsEndpoint} from './mock-handlers';

// Pairs with the BPMN_XML served by processInstanceHeaderHandlers: an incident on task-1, and a taken sequence flow.
function processInstanceDiagramHandlers() {
	return [
		mockGetProcessInstanceStatisticsEndpoint({
			successResponse: HttpResponse.json({
				items: [
					createProcessDefinitionStatistic({elementId: 'task-1', active: 1, incidents: 1}),
					createProcessDefinitionStatistic({elementId: 'start_event', completed: 1}),
				],
			}),
		}),
		mockGetProcessInstanceSequenceFlowsEndpoint({
			successResponse: HttpResponse.json({items: [{elementId: 'Flow_0xb8om9'}]}),
		}),
	];
}

export {processInstanceDiagramHandlers};
