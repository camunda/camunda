/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

/* istanbul ignore file */

import {MemoryRouter} from 'react-router-dom';
import {Story} from '@storybook/react';
import {Dashboard as DashboardComponent} from './index';
import {rest} from 'msw';
import {statistics} from 'modules/mocks/statistics';
import {incidentsByProcess} from 'modules/mocks/incidentsByProcess';
import {incidentsByError} from 'modules/mocks/incidentsByError';
import {user} from 'modules/mocks/user';
import {Layout} from 'App/Layout';

export default {
  title: 'Pages/Dashboard',
};

const Success: Story = () => {
  return (
    <MemoryRouter>
      <Layout>
        <DashboardComponent />
      </Layout>
    </MemoryRouter>
  );
};

Success.parameters = {
  msw: [
    rest.get('/api/authentications/user', (_, res, ctx) => {
      return res(ctx.json(user));
    }),
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
  return (
    <MemoryRouter>
      <Layout>
        <DashboardComponent />
      </Layout>
    </MemoryRouter>
  );
};

Skeleton.parameters = {
  msw: [
    rest.get('/api/authentications/user', (_, res, ctx) => {
      return res(ctx.json(user));
    }),
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
  return (
    <MemoryRouter>
      <Layout>
        <DashboardComponent />
      </Layout>
    </MemoryRouter>
  );
};

Error.parameters = {
  msw: [
    rest.get('/api/authentications/user', (_, res, ctx) => {
      return res(ctx.json(user));
    }),
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
