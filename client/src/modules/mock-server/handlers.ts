/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {RequestHandler, rest} from 'msw';
import {mockDmnXml} from 'modules/mocks/mockDmnXml';
import {
  assignApproverGroup,
  invoiceClassification,
} from 'modules/mocks/mockDecisionInstance';
import {mockLiteralExpression} from 'modules/mocks/mockLiteralExpression';
import {mockDecisionInstancesLargeData} from 'modules/mocks/mockDecisionInstances';
import {mockDrdData} from 'modules/mocks/mockDrdData';
import {MetaDataEntity} from 'modules/stores/flowNodeMetaData';
import {
  calledDecisionMetadata,
  calledFailedDecisionMetadata,
  calledUnevaluatedDecisionMetadata,
} from 'modules/mocks/metadata';
import {groupedDecisions} from 'modules/mocks/groupedDecisions';

const handlers: RequestHandler[] = [
  rest.get('/api/decision-instances/:decisionInstanceId', (req, res, ctx) => {
    const {decisionInstanceId} = req.params;

    if (decisionInstanceId === '0') {
      return res(ctx.json(assignApproverGroup));
    }
    if (decisionInstanceId === '2') {
      return res(ctx.json(mockLiteralExpression));
    }
    return res(ctx.json(invoiceClassification));
  }),
  rest.get(
    '/api/decision-instances/:decisionInstanceId/drd-data',
    (_, res, ctx) => {
      return res(ctx.json(mockDrdData));
    }
  ),
  rest.get('/api/decisions/:decisionDefinitionId/xml', (_, res, ctx) => {
    return res(ctx.delay(1000), ctx.body(mockDmnXml));
  }),
  rest.post('/api/decision-instances', (req, res, ctx) => {
    //@ts-ignore
    if (!req.body.searchAfter && !req.body.searchBefore) {
      return res(
        ctx.delay(1000),
        ctx.json({
          decisionInstances: [
            ...mockDecisionInstancesLargeData.decisionInstances.slice(0, 20),
          ],
          totalCount: mockDecisionInstancesLargeData.totalCount,
        })
      );
    }

    if (
      //@ts-ignore
      JSON.stringify(req.body.searchAfter) ===
      JSON.stringify(['test-decision-instance-20', '2251799813689560'])
    ) {
      return res(
        ctx.json({
          decisionInstances: [
            ...mockDecisionInstancesLargeData.decisionInstances.slice(20),
          ],
          totalCount: mockDecisionInstancesLargeData.totalCount,
        })
      );
    }

    if (
      //@ts-ignore
      JSON.stringify(req.body.searchBefore) ===
      JSON.stringify(['test-decision-instance-11', '2251799813689551'])
    ) {
      return res(
        ctx.json({
          decisionInstances: [
            ...mockDecisionInstancesLargeData.decisionInstances.slice(0, 10),
          ],
          totalCount: mockDecisionInstancesLargeData.totalCount,
        })
      );
    }

    if (
      //@ts-ignore
      JSON.stringify(req.body.searchAfter) ===
      JSON.stringify(['test-decision-instance-40', '2251799813689580'])
    ) {
      return res(
        ctx.json({
          decisionInstances: [],
          totalCount: mockDecisionInstancesLargeData.totalCount,
        })
      );
    }

    return res(ctx.json(mockDecisionInstancesLargeData));
  }),
  rest.post(
    '/api/process-instances/:processInstanceId/flow-node-metadata',
    async (req, res, ctx) => {
      const response = await ctx.fetch(req);
      const metadata: MetaDataEntity = await response.json();

      if (metadata.instanceMetadata?.flowNodeType === 'BUSINESS_RULE_TASK') {
        if (metadata.incident !== null) {
          return res(ctx.json(calledFailedDecisionMetadata));
        }

        if (metadata.instanceMetadata.endDate !== null) {
          return res(ctx.json(calledDecisionMetadata));
        }

        return res(ctx.json(calledUnevaluatedDecisionMetadata));
      }
      return res(ctx.json(metadata));
    }
  ),
  rest.get('/api/decisions/grouped', async (_, res, ctx) => {
    return res(ctx.json(groupedDecisions));
  }),
];

export {handlers};
