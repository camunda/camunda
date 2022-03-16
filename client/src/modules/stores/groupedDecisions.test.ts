/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {waitFor} from '@testing-library/react';
import {mockServer} from 'modules/mock-server/node';
import {groupedDecisions} from 'modules/mocks/groupedDecisions';
import {rest} from 'msw';
import {groupedDecisionsStore} from './groupedDecisions';

describe('groupedDecisionsStore', () => {
  afterEach(() => {
    groupedDecisionsStore.reset();
  });

  describe('fetch success', () => {
    beforeEach(async () => {
      mockServer.use(
        rest.get('/api/decisions/grouped', (_, res, ctx) =>
          res.once(ctx.json(groupedDecisions))
        )
      );

      expect(groupedDecisionsStore.state.status).toBe('initial');

      groupedDecisionsStore.fetchDecisions();

      await waitFor(() => {
        expect(groupedDecisionsStore.state.status).toBe('fetched');
      });
    });

    it('should fetch grouped decisions', async () => {
      expect(groupedDecisionsStore.state.decisions).toEqual(groupedDecisions);
      expect(groupedDecisionsStore.areDecisionsEmpty).toBe(false);
    });

    it('should get decision definition id', async () => {
      expect(
        groupedDecisionsStore.getDecisionDefinitionId({
          decisionId: 'invoice-assign-approver',
          version: 1,
        })
      ).toEqual('0');
    });

    it('should return null for invalid decision ids', async () => {
      expect(
        groupedDecisionsStore.getDecisionDefinitionId({
          decisionId: 'invalidDecisionId',
          version: 1,
        })
      ).toEqual(null);
    });
  });

  describe('fetch error', () => {
    beforeEach(async () => {
      mockServer.use(
        rest.get('/api/decisions/grouped', (_, res, ctx) =>
          res.once(ctx.status(500))
        )
      );

      groupedDecisionsStore.fetchDecisions();

      await waitFor(() => {
        expect(groupedDecisionsStore.state.status === 'error').toBe(true);
      });
    });

    it('should keep decisions empty', () => {
      expect(groupedDecisionsStore.state.decisions).toEqual([]);
      expect(groupedDecisionsStore.areDecisionsEmpty).toBe(true);
    });

    it('should return null', () => {
      expect(
        groupedDecisionsStore.getDecisionDefinitionId({
          decisionId: 'invoice-assign-approver',
          version: 1,
        })
      ).toBe(null);
    });
  });
});
