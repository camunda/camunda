/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {GetProcessSequenceFlowsResponseBody} from '@vzeta/camunda-api-zod-schemas/operate';
import {RequestHandler, RestRequest, rest} from 'msw';

const mockEndpoints = [
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
];

const handlers: RequestHandler[] = [...mockEndpoints];

export {handlers};
