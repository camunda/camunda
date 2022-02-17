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
import {mockDecisionInstances} from 'modules/mocks/mockDecisionInstances';
import {mockDrdData} from 'modules/mocks/mockDrdData';

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
    return res(ctx.body(mockDmnXml));
  }),
  rest.post('/api/decision-instances', (_, res, ctx) => {
    return res(ctx.delay(1000), ctx.json(mockDecisionInstances));
  }),
];

export {handlers};
