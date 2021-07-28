/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

/* istanbul ignore file */

import {MemoryRouter} from 'react-router-dom';
import {Story} from '@storybook/react';

import {InstancesByProcess as InstancesByProcessComponent} from './index';
import {rest} from 'msw';
import styled from 'styled-components';
import {incidentsByProcess} from 'modules/mocks/incidentsByProcess';

export default {
  title: 'Components/InstancesByProcess',
};

const Container = styled.div`
  width: 638px;
  height: 388px;
  overflow: scroll;
  background-color: #fdfdfe;
`;

const InstancesByProcess: Story = () => {
  return (
    <MemoryRouter>
      <Container>
        <InstancesByProcessComponent />
      </Container>
    </MemoryRouter>
  );
};

InstancesByProcess.parameters = {
  msw: [
    rest.get('/api/incidents/byProcess', (_, res, ctx) => {
      return res(ctx.json(incidentsByProcess));
    }),
  ],
};

InstancesByProcess.storyName = 'InstancesByProcess - Success';

const InstancesByProcessLoading: Story = () => {
  return (
    <MemoryRouter>
      <Container>
        <InstancesByProcessComponent />
      </Container>
    </MemoryRouter>
  );
};

InstancesByProcessLoading.storyName = 'InstancesByProcess - Loading';
InstancesByProcessLoading.parameters = {
  msw: [
    rest.get('/api/incidents/byProcess', (_, res, ctx) => {
      return res(ctx.delay('infinite'), ctx.json({}));
    }),
  ],
};
const InstancesByProcessFailure: Story = () => {
  return (
    <MemoryRouter>
      <Container>
        <InstancesByProcessComponent />
      </Container>
    </MemoryRouter>
  );
};

InstancesByProcessFailure.storyName = 'InstancesByProcess - Failure';
InstancesByProcessFailure.parameters = {
  msw: [
    rest.get('/api/incidents/byProcess', (_, res, ctx) => {
      return res(ctx.status(500), ctx.json({}));
    }),
  ],
};

export {
  InstancesByProcess,
  InstancesByProcessLoading,
  InstancesByProcessFailure,
};
