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

const Dashboard: Story = () => {
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

Dashboard.storyName = 'Dashboard - Success';
Dashboard.parameters = {
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

const DashboardSkeleton: Story = () => {
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

DashboardSkeleton.storyName = 'Dashboard - Skeleton';
DashboardSkeleton.parameters = {
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

const DashboardFailure: Story = () => {
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

DashboardFailure.storyName = 'Dashboard - Failure';
DashboardFailure.parameters = {
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

export {Dashboard, DashboardSkeleton, DashboardFailure};
