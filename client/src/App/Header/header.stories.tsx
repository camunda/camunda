/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

/* istanbul ignore file */

import {MemoryRouter} from 'react-router-dom';
import {Story} from '@storybook/react';

import Header from './index';
import {rest} from 'msw';
import {useEffect} from 'react';
import {currentInstanceStore} from 'modules/stores/currentInstance';
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
  title: 'Components/Header',
  parameters: {
    msw: [...mocks],
  },
};

const Dashboard: React.FC = () => {
  return (
    <MemoryRouter initialEntries={['']}>
      <Header />
    </MemoryRouter>
  );
};

const InstanceDetailSkeleton: Story = () => {
  return (
    <MemoryRouter initialEntries={['/instances/2251799813686518']}>
      <Header />
    </MemoryRouter>
  );
};

InstanceDetailSkeleton.storyName = 'Instance Detail - Skeleton';

const InstanceDetailIncident: Story = () => {
  useEffect(() => {
    currentInstanceStore.init('2251799813686518');

    return () => currentInstanceStore.reset();
  }, []);
  return (
    <MemoryRouter initialEntries={['/instances/2251799813686518']}>
      <Header />
    </MemoryRouter>
  );
};

InstanceDetailIncident.storyName = 'Instance Detail - Incident';
InstanceDetailIncident.parameters = {
  msw: [
    ...mocks,
    rest.get('/api/process-instances/2251799813686518', (_, res, ctx) => {
      return res(
        ctx.json({
          id: '2251799813686518',
          processId: '2251799813686375',
          processName: 'Order process',
          processVersion: 1,
          startDate: '2021-07-07T10:07:48.946+0000',
          endDate: null,
          state: 'INCIDENT',
          bpmnProcessId: 'orderProcess',
          hasActiveOperation: false,
          operations: [],
          parentInstanceId: null,
          sortValues: null,
        })
      );
    }),
  ],
};

const InstanceDetailActive: Story = () => {
  useEffect(() => {
    currentInstanceStore.init('2251799813686518');
    return () => currentInstanceStore.reset();
  }, []);
  return (
    <MemoryRouter initialEntries={['/instances/2251799813686518']}>
      <Header />
    </MemoryRouter>
  );
};

InstanceDetailActive.storyName = 'Instance Detail - Active';
InstanceDetailActive.parameters = {
  msw: [
    ...mocks,
    rest.get('/api/process-instances/2251799813686518', (_, res, ctx) => {
      return res(
        ctx.json({
          id: '2251799813686518',
          processId: '2251799813685450',
          processName: 'Without Incidents Process',
          processVersion: 1,
          startDate: '2021-07-08T13:10:02.485+0000',
          endDate: null,
          state: 'ACTIVE',
          bpmnProcessId: 'withoutIncidentsProcess',
          hasActiveOperation: false,
          operations: [],
          parentInstanceId: null,
          sortValues: null,
        })
      );
    }),
  ],
};

const InstanceDetailCanceled: Story = () => {
  useEffect(() => {
    currentInstanceStore.init('2251799813686518');
    return () => currentInstanceStore.reset();
  }, []);
  return (
    <MemoryRouter initialEntries={['/instances/2251799813686518']}>
      <Header />
    </MemoryRouter>
  );
};

InstanceDetailCanceled.storyName = 'Instance Detail - Canceled';
InstanceDetailCanceled.parameters = {
  msw: [
    ...mocks,
    rest.get('/api/process-instances/2251799813686518', (_, res, ctx) => {
      return res(
        ctx.json({
          id: '2251799813686518',
          processId: '2251799813686309',
          processName: 'Sequential Multi-Instance Process',
          processVersion: 1,
          startDate: '2021-07-08T13:10:40.581+0000',
          endDate: '2021-07-08T13:20:53.251+0000',
          state: 'CANCELED',
          bpmnProcessId: 'multiInstanceProcess',
          hasActiveOperation: false,
          operations: [],
          parentInstanceId: null,
          sortValues: null,
        })
      );
    }),
  ],
};

const InstanceDetailCompleted: Story = () => {
  useEffect(() => {
    currentInstanceStore.init('2251799813686518');
    return () => currentInstanceStore.reset();
  }, []);
  return (
    <MemoryRouter initialEntries={['/instances/2251799813686518']}>
      <Header />
    </MemoryRouter>
  );
};

InstanceDetailCompleted.storyName = 'Instance Detail - Completed';
InstanceDetailCompleted.parameters = {
  msw: [
    ...mocks,
    rest.get('/api/process-instances/2251799813686518', (_, res, ctx) => {
      return res(
        ctx.json({
          id: '2251799813686518',
          processId: '2251799813686322',
          processName: 'Timer process',
          processVersion: 1,
          startDate: '2021-07-08T13:13:29.951+0000',
          endDate: '2021-07-08T13:18:30.052+0000',
          state: 'COMPLETED',
          bpmnProcessId: 'timerProcess',
          hasActiveOperation: false,
          operations: [],
          parentInstanceId: null,
          sortValues: null,
        })
      );
    }),
  ],
};

export {
  Dashboard,
  InstanceDetailSkeleton,
  InstanceDetailIncident,
  InstanceDetailActive,
  InstanceDetailCanceled,
  InstanceDetailCompleted,
};
