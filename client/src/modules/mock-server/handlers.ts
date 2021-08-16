/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {IS_NEXT_INCIDENTS} from 'modules/feature-flags';
import {Incident} from 'modules/stores/incidents';
import {RequestHandler, rest} from 'msw';

type IncidentsResponse = {
  flowNodes: [{flowNodeId: string; count: number}];
  errorTypes: [{errorType: string; count: number}];
  incidents: any;
  count: number;
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

      const incidents = parsedResponse.incidents.map((incident: Incident) => {
        if (incident.flowNodeId === 'call-activity') {
          return {
            ...incident,
            rootCauseInstance: {
              instanceId: '11111111111111111',
              processDefinitionId: '00000000000000000',
              processDefinitionName: 'Called Process',
            },
          };
        } else {
          return {
            ...incident,
            rootCauseInstance: null,
          };
        }
      });

      const flowNodes = parsedResponse.flowNodes.map((flowNode) => ({
        id: flowNode.flowNodeId,
        count: flowNode.count,
      }));

      const errorTypes = parsedResponse.errorTypes.map((errorType) => ({
        id: errorType.errorType,
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
];

export {handlers};
