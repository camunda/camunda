/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  GetProcessDefinitionStatisticsRequestBody,
  GetProcessDefinitionStatisticsResponseBody,
  GetProcessSequenceFlowsResponseBody,
  GetProcessInstanceCallHierarchyResponseBody,
  ProcessInstance,
} from '@vzeta/camunda-api-zod-schemas/operate';
import {ProcessInstanceState} from 'modules/api/v2/processInstances/fetchProcessInstancesStatistics';
import {RequestHandler, RestRequest, rest} from 'msw';

const mockEndpoints = [
  rest.post(
    '/v2/process-definitions/:processDefinitionKey/statistics/element-instances',
    async (
      req: RestRequest<GetProcessDefinitionStatisticsRequestBody>,
      res,
      ctx,
    ) => {
      const mockResponse: GetProcessDefinitionStatisticsResponseBody = {
        items: [
          {
            elementId: 'Gateway_15jzrqe',
            active: 0,
            canceled: 0,
            incidents: 20,
            completed: 0,
          },
          {
            elementId: 'exclusiveGateway',
            active: 0,
            canceled: 0,
            incidents: 20,
            completed: 0,
          },
          {
            elementId: 'alwaysFailingTask',
            active: 20,
            canceled: 0,
            incidents: 0,
            completed: 0,
          },
          {
            elementId: 'messageCatchEvent',
            active: 0,
            canceled: 0,
            incidents: 20,
            completed: 0,
          },
          {
            elementId: 'upperTask',
            active: 20,
            canceled: 0,
            incidents: 0,
            completed: 0,
          },
        ],
      };

      return res(ctx.json(mockResponse));
    },
  ),
  rest.get(
    '/v2/process-instances/:processInstanceKey/sequence-flows',
    async (req: RestRequest<GetProcessSequenceFlowsResponseBody>, res, ctx) => {
      const mockResponse: GetProcessSequenceFlowsResponseBody = {
        items: [
          {
            processInstanceKey: '2251799814752788',
            sequenceFlowKey: 'Flow_0z4mequ',
            processDefinitionKey: 2251799814751761,
            processDefinitionId: '2251799814751761',
          },
          {
            processInstanceKey: '2251799814752788',
            sequenceFlowKey: 'SequenceFlow_0oxsuty',
            processDefinitionKey: 2251799814751761,
            processDefinitionId: '2251799814751761',
          },
          {
            processInstanceKey: '2251799814752788',
            sequenceFlowKey: 'SequenceFlow_12gxvr0',
            processDefinitionKey: 2251799814751761,
            processDefinitionId: '2251799814751761',
          },
          {
            processInstanceKey: '2251799814752788',
            sequenceFlowKey: 'SequenceFlow_1gvaaro',
            processDefinitionKey: 2251799814751761,
            processDefinitionId: '2251799814751761',
          },
          {
            processInstanceKey: '2251799814752788',
            sequenceFlowKey: 'SequenceFlow_1j24jks',
            processDefinitionKey: 2251799814751761,
            processDefinitionId: '2251799814751761',
          },
          {
            processInstanceKey: '2251799814752788',
            sequenceFlowKey: 'SequenceFlow_1ti40d3',
            processDefinitionKey: 2251799814751761,
            processDefinitionId: '2251799814751761',
          },
        ],
      };

      return res(ctx.json(mockResponse));
    },
  ),
  rest.get(
    '/v2/process-instances/:processInstanceKey',
    async (req, res, ctx) => {
      const mockResponse: ProcessInstance = {
        processDefinitionId: 'complexProcess',
        processDefinitionName: 'complexProcess',
        processDefinitionVersion: 156,
        startDate: '2025-04-17T11:20:09.654Z',
        state: ProcessInstanceState.ACTIVE,
        hasIncident: true,
        tenantId: '\u003Cdefault\u003E',
        processInstanceKey: '2251799814752788',
        processDefinitionKey: '2251799814751761',
      };

      return res(ctx.json(mockResponse));
    },
  ),
  rest.get(
    '/v2/process-instances/:processInstanceKey/call-hierarchy',
    async (req, res, ctx) => {
      const mockResponse: GetProcessInstanceCallHierarchyResponseBody = {
        items: [
          {
            processInstanceKey: '2251799833223965',
            processDefinitionName: 'Call Activity Process',
          },
          {
            processInstanceKey: '2251799833223971',
            processDefinitionName: 'called-process',
          },
        ],
      };

      return res(ctx.json(mockResponse));
    },
  ),
];

const handlers: RequestHandler[] = [...mockEndpoints];

export {handlers};
