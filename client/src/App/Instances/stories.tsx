/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

/* istanbul ignore file */

import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {Story} from '@storybook/react';

import {Instances as InstancesComponent} from './index';
import {rest} from 'msw';
import {mockGroupedProcesses} from 'modules/mocks/mockGroupedProcesses';
import {mockProcessXml} from 'modules/mocks/mockProcessXml';
import {mockProcessInstances} from 'modules/mocks/mockProcessInstances';
import {mockBatchOperations} from 'modules/mocks/mockBatchOperations';
import {Layout} from 'App/Layout';
import {statistics} from 'modules/mocks/statistics';
import {user} from 'modules/mocks/user';

const mocks = [
  rest.get('/api/authentications/user', (_, res, ctx) => {
    return res(ctx.json(user));
  }),
  rest.get('/api/process-instances/core-statistics', (_, res, ctx) => {
    return res(ctx.json(statistics));
  }),
];

export default {
  title: 'Pages/Processes',
};

const Success: Story = () => {
  return (
    <MemoryRouter
      initialEntries={[
        '/processes?active=true&incidents=true&process=bigVarProcess&version=1',
      ]}
    >
      <Routes>
        <Route path="/processes" element={<Layout />}>
          <Route index element={<InstancesComponent />} />
        </Route>
      </Routes>
    </MemoryRouter>
  );
};

Success.parameters = {
  msw: [
    ...mocks,
    rest.post('/api/process-instances/statistics', (_, res, ctx) => {
      return res(
        ctx.json([
          {
            activityId: 'ServiceTask_0kt6c5i',
            active: 2,
            canceled: 0,
            incidents: 0,
            completed: 0,
          },
        ])
      );
    }),
    rest.get('/api/processes/grouped', (_, res, ctx) => {
      return res(ctx.json(mockGroupedProcesses));
    }),
    rest.get('/api/processes/:instanceId/xml', (_, res, ctx) =>
      res(ctx.text(mockProcessXml))
    ),
    rest.post('/api/process-instances', (_, res, ctx) => {
      return res(ctx.json(mockProcessInstances));
    }),
    rest.post('/api/batch-operations', (_, res, ctx) =>
      res(ctx.json(mockBatchOperations))
    ),
  ],
};

const Error: Story = () => {
  return (
    <MemoryRouter initialEntries={['/processes?incidents=true']}>
      <Routes>
        <Route path="/processes" element={<Layout />}>
          <Route index element={<InstancesComponent />} />
        </Route>
      </Routes>
    </MemoryRouter>
  );
};

Error.parameters = {
  msw: [
    ...mocks,
    rest.post('/api/process-instances/statistics', (_, res, ctx) => {
      return res(
        ctx.json([
          {
            activityId: 'ServiceTask_0kt6c5i',
            active: 2,
            canceled: 0,
            incidents: 0,
            completed: 0,
          },
        ])
      );
    }),
    rest.get('/api/processes/grouped', (_, res, ctx) => {
      return res(ctx.json(mockGroupedProcesses));
    }),
    rest.get('/api/processes/:instanceId/xml', (_, res, ctx) =>
      res(ctx.status(500), ctx.text(''))
    ),
    rest.post('/api/process-instances', (_, res, ctx) => {
      return res(ctx.status(500), ctx.json({}));
    }),
    rest.post('/api/batch-operations', (_, res, ctx) =>
      res(ctx.status(500), ctx.json(''))
    ),
  ],
};

const Skeleton: Story = () => {
  return (
    <MemoryRouter
      initialEntries={[
        '/processes?active=true&incidents=true&process=bigVarProcess&version=1',
      ]}
    >
      <Routes>
        <Route path="/processes" element={<Layout />}>
          <Route index element={<InstancesComponent />} />
        </Route>
      </Routes>
    </MemoryRouter>
  );
};

Skeleton.parameters = {
  msw: [
    ...mocks,
    rest.post('/api/process-instances/statistics', (_, res, ctx) => {
      return res(ctx.delay('infinite'), ctx.json({}));
    }),
    rest.get('/api/processes/grouped', (_, res, ctx) => {
      return res(ctx.delay('infinite'), ctx.json({}));
    }),
    rest.get('/api/processes/:instanceId/xml', (_, res, ctx) =>
      res(ctx.delay('infinite'), ctx.text(''))
    ),
    rest.post('/api/process-instances', (_, res, ctx) => {
      return res(ctx.delay('infinite'), ctx.json({}));
    }),
    rest.post('/api/batch-operations', (_, res, ctx) =>
      res(ctx.delay('infinite'), ctx.json(''))
    ),
  ],
};

const NoFilterApplied: Story = () => {
  return (
    <MemoryRouter initialEntries={['/processes']}>
      <Routes>
        <Route path="/processes" element={<Layout />}>
          <Route index element={<InstancesComponent />} />
        </Route>
      </Routes>
    </MemoryRouter>
  );
};

NoFilterApplied.parameters = {
  msw: [
    ...mocks,
    rest.get('/api/processes/grouped', (_, res, ctx) => {
      return res(ctx.json(mockGroupedProcesses));
    }),
    rest.post('/api/process-instances', (_, res, ctx) => {
      return res(ctx.json({processInstances: [], totalCount: 0}));
    }),
    rest.post('/api/batch-operations', (_, res, ctx) =>
      res(ctx.status(200), ctx.json([]))
    ),
  ],
};

const Empty: Story = () => {
  return (
    <MemoryRouter initialEntries={['/processes?active=true&incidents=true']}>
      <Routes>
        <Route path="/processes" element={<Layout />}>
          <Route index element={<InstancesComponent />} />
        </Route>
      </Routes>
    </MemoryRouter>
  );
};

Empty.parameters = {
  msw: [
    ...mocks,
    rest.get('/api/processes/grouped', (_, res, ctx) => {
      return res(ctx.json(mockGroupedProcesses));
    }),
    rest.post('/api/process-instances', (_, res, ctx) => {
      return res(ctx.json({processInstances: [], totalCount: 0}));
    }),
    rest.post('/api/batch-operations', (_, res, ctx) =>
      res(ctx.status(200), ctx.json([]))
    ),
  ],
};

const NoVersionSelected: Story = () => {
  return (
    <MemoryRouter
      initialEntries={[
        '/processes?active=true&incidents=true&process=complexProcess&version=all',
      ]}
    >
      <Routes>
        <Route path="/processes" element={<Layout />}>
          <Route index element={<InstancesComponent />} />
        </Route>
      </Routes>
    </MemoryRouter>
  );
};

NoVersionSelected.parameters = {
  msw: [
    ...mocks,
    rest.post('/api/process-instances/statistics', (_, res, ctx) => {
      return res(
        ctx.json([
          {
            activityId: 'ServiceTask_0kt6c5i',
            active: 2,
            canceled: 0,
            incidents: 0,
            completed: 0,
          },
        ])
      );
    }),
    rest.get('/api/processes/grouped', (_, res, ctx) => {
      return res(ctx.json(mockGroupedProcesses));
    }),
    rest.get('/api/processes/:instanceId/xml', (_, res, ctx) =>
      res(ctx.text(mockProcessXml))
    ),
    rest.post('/api/process-instances', (_, res, ctx) => {
      return res(ctx.json(mockProcessInstances));
    }),
    rest.post('/api/batch-operations', (_, res, ctx) =>
      res(ctx.json(mockBatchOperations))
    ),
  ],
};

export {Success, Error, Skeleton, NoFilterApplied, Empty, NoVersionSelected};
