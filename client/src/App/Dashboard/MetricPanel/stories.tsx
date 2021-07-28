/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

/* istanbul ignore file */

import {MemoryRouter} from 'react-router-dom';
import {Story} from '@storybook/react';

import {MetricPanel} from './index';
import {statisticsStore} from 'modules/stores/statistics';
import {rest} from 'msw';
import {useEffect} from 'react';
import {statistics} from 'modules/mocks/statistics';

export default {
  title: 'Components/MetricPanel',
};

const Metrics: Story = () => {
  useEffect(() => {
    statisticsStore.init();
    return () => statisticsStore.reset();
  }, []);

  return (
    <MemoryRouter>
      <MetricPanel />
    </MemoryRouter>
  );
};

Metrics.parameters = {
  msw: [
    rest.get('/api/process-instances/core-statistics', (_, res, ctx) => {
      return res(ctx.json(statistics));
    }),
  ],
};

const MetricsFailed: Story = () => {
  useEffect(() => {
    statisticsStore.init();
    return () => statisticsStore.reset();
  }, []);

  return (
    <MemoryRouter>
      <MetricPanel />
    </MemoryRouter>
  );
};

MetricsFailed.storyName = 'Metrics - Failed';
MetricsFailed.parameters = {
  msw: [
    rest.get('/api/process-instances/core-statistics', (_, res, ctx) => {
      return res(ctx.status(500), ctx.json({}));
    }),
  ],
};

const MetricsLoading: Story = () => {
  return (
    <MemoryRouter>
      <MetricPanel />
    </MemoryRouter>
  );
};

MetricsLoading.storyName = 'Metrics - Loading';

export {Metrics, MetricsFailed, MetricsLoading};
