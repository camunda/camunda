/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {IS_BREADCRUMB_VISIBLE, IS_NEXT_INCIDENTS} from 'modules/feature-flags';
import {RequestHandler, rest} from 'msw';
import {incidentsStore} from 'modules/stores/incidents';

type IncidentsResponse = {
  flowNodes: [{flowNodeId: string; count: number}];
  errorTypes: [{errorType: string; count: number}];
  incidents: any;
  count: number;
};

const ERROR_TYPES: {[errorType: string]: string} = {
  'I/O mapping error': 'IO_MAPPING_ERROR',
  'No more retries left': 'JOB_NO_RETRIES',
  'Extract value error': 'EXTRACT_VALUE_ERROR',
  'Condition error': 'CONDITION_ERROR',
};

const handlers: RequestHandler[] = [
  rest.get(
    '/api/process-instances/:instanceId/incidents',
    async (req, res, ctx) => {
      const response = await ctx.fetch(req);

      if (!IS_NEXT_INCIDENTS) {
        return res(ctx.json(await response.json()));
      }

      const parsedResponse: IncidentsResponse = await response.json();

      const incidents = parsedResponse.incidents.map((incident: any) => {
        const errorType = {
          id: ERROR_TYPES[incident.errorType] || incident.errorType,
          name: incident.errorType,
        };
        if (incident.flowNodeId === 'call-activity') {
          return {
            ...incident,
            errorType,
            rootCauseInstance: {
              instanceId: '11111111111111111',
              processDefinitionId: '00000000000000000',
              processDefinitionName: 'Called Process',
            },
          };
        } else {
          return {
            ...incident,
            errorType,
            rootCauseInstance: null,
          };
        }
      });

      const flowNodes = parsedResponse.flowNodes.map((flowNode) => ({
        id: flowNode.flowNodeId,
        count: flowNode.count,
      }));

      const errorTypes = parsedResponse.errorTypes.map((errorType) => ({
        id: ERROR_TYPES[errorType.errorType] || errorType.errorType,
        name: errorType.errorType,
        count: errorType.count,
      }));

      return res(
        ctx.json({
          incidents,
          flowNodes,
          errorTypes,
          count: parsedResponse.count,
        })
      );
    }
  ),
  rest.post(
    '/api/process-instances/:processInstanceId/flow-node-metadata',
    async (req, res, ctx) => {
      const response = await ctx.fetch(req);

      if (!IS_NEXT_INCIDENTS) {
        return res(ctx.json(await response.json()));
      }

      const parsedResponse = await response.json();
      const {instanceMetadata} = parsedResponse;

      if (instanceMetadata && instanceMetadata.incidentErrorType) {
        const {incidentErrorType, incidentErrorMessage, flowNodeType} =
          instanceMetadata;
        const errorTypeName =
          Object.entries(ERROR_TYPES).find(
            ([errorTypeName, errorTypeId]) => incidentErrorType === errorTypeId
          )?.[0] || incidentErrorType;

        const incident = {
          errorType: {
            id: incidentErrorType,
            name: errorTypeName,
          },
          errorMessage: incidentErrorMessage,
          rootCauseInstance:
            flowNodeType === 'CALL_ACTIVITY'
              ? {
                  instanceId: '11111111111111111',
                  processDefinitionId: '00000000000000000',
                  processDefinitionName: 'Called Process',
                }
              : null,
        };

        delete parsedResponse.instanceMetadata.incidentErrorType;
        delete parsedResponse.instanceMetadata.incidentErrorMessage;

        return res(
          ctx.json({
            ...parsedResponse,
            incidentCount: 1,
            incident,
          })
        );
      } else {
        const hasIncidents = incidentsStore.incidents.some(
          (incident) => incident.flowNodeId === parsedResponse.flowNodeId
        );
        return res(
          ctx.json({
            ...parsedResponse,
            incidentCount: instanceMetadata === null && hasIncidents ? 3 : null,
            incident: null,
          })
        );
      }
    }
  ),

  rest.get('/api/process-instances/core-statistics', async (req, res, ctx) => {
    const response = await ctx.fetch(req);
    return res(ctx.json(await response.json()));
  }),

  rest.get('/api/process-instances/:id', async (req, res, ctx) => {
    const response = await ctx.fetch(req);

    if (!IS_BREADCRUMB_VISIBLE) {
      return res(ctx.json(await response.json()));
    }

    const parsedResponse = await response.json();

    return res(
      ctx.json({
        ...parsedResponse,
        callHierarchy: [
          {
            instanceId: '546546543276',
            processDefinitionName: 'Parent Process Name',
          },
          {
            instanceId: '968765314354',
            processDefinitionName: '1st level Child Process Name',
          },
          {
            instanceId: '2251799813685447',
            processDefinitionName: '2nd level Child Process Name',
          },
        ],
      })
    );
  }),
];

export {handlers};
