/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {RequestHandler, rest} from 'msw';
import {mockDmnXml} from 'modules/mocks/mockDmnXml';
import {mockDecisionInstance} from 'modules/mocks/mockDecisionInstance';

const handlers: RequestHandler[] = [
  rest.get('/api/decision-instances/:decisionInstanceId', (req, res, ctx) => {
    return res(ctx.json(mockDecisionInstance));
  }),

  rest.get('/api/decisions/:decisionDefinitionId/xml', (req, res, ctx) => {
    return res(ctx.body(mockDmnXml));
  }),
];

export {handlers};
