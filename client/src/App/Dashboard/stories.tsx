/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

/* istanbul ignore file */

import {MemoryRouter} from 'react-router-dom';
import {Story} from '@storybook/react';

import {Dashboard as DashboardComponent} from './index';
import {statisticsStore} from 'modules/stores/statistics';
import {rest} from 'msw';
import {useEffect} from 'react';
import {statistics} from 'modules/mocks/statistics';
import {incidentsByProcess} from 'modules/mocks/incidentsByProcess';
import {incidentsByError} from 'modules/mocks/incidentsByError';

export default {
  title: 'Pages/Dashboard',
};

const Success: Story = () => {
  useEffect(() => {
    statisticsStore.init();
    return () => statisticsStore.reset();
  }, []);

  return (
    <MemoryRouter>
      <DashboardComponent />
    </MemoryRouter>
  );
};

Success.parameters = {
  msw: [
    rest.get('/api/process-instances/core-statistics', (_, res, ctx) => {
      return res(ctx.json(statistics));
    }),
    rest.get('/api/incidents/byProcess', (_, res, ctx) => {
      return res(ctx.json(incidentsByProcess));
    }),
    rest.get('/api/incidents/byError', (_, res, ctx) => {
      return res(ctx.json(incidentsByError));
    }),
  ],
};

const Skeleton: Story = () => {
  useEffect(() => {
    statisticsStore.init();
    return () => statisticsStore.reset();
  }, []);

  return (
    <MemoryRouter>
      <DashboardComponent />
    </MemoryRouter>
  );
};

Skeleton.parameters = {
  msw: [
    rest.get('/api/process-instances/core-statistics', (_, res, ctx) => {
      return res(ctx.delay('infinite'), ctx.json({}));
    }),
    rest.get('/api/incidents/byProcess', (_, res, ctx) => {
      return res(ctx.delay('infinite'), ctx.json([]));
    }),
    rest.get('/api/incidents/byError', (_, res, ctx) => {
      return res(ctx.delay('infinite'), ctx.json([]));
    }),
  ],
};

const Error: Story = () => {
  useEffect(() => {
    statisticsStore.init();
    return () => statisticsStore.reset();
  }, []);

  return (
    <MemoryRouter>
      <DashboardComponent />
    </MemoryRouter>
  );
};

Error.parameters = {
  msw: [
    rest.get('/api/process-instances/core-statistics', (_, res, ctx) => {
      return res(ctx.status(500), ctx.json({}));
    }),
    rest.get('/api/incidents/byProcess', (_, res, ctx) => {
      return res(ctx.status(500), ctx.json([]));
    }),
    rest.get('/api/incidents/byError', (_, res, ctx) => {
      return res(ctx.status(500), ctx.json([]));
    }),
  ],
};

export {Success, Skeleton, Error};
