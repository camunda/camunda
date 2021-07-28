/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

/* istanbul ignore file */

import {MemoryRouter} from 'react-router-dom';
import {Story} from '@storybook/react';

import {IncidentsByError as IncidentsByErrorComponent} from './index';
import {rest} from 'msw';
import styled from 'styled-components';
import {incidentsByError} from 'modules/mocks/incidentsByError';

export default {
  title: 'Components/IncidentsByError',
};

const Container = styled.div`
  width: 638px;
  height: 388px;
  overflow: scroll;
  background-color: #fdfdfe;
`;

const IncidentsByError: Story = () => {
  return (
    <MemoryRouter>
      <Container>
        <IncidentsByErrorComponent />
      </Container>
    </MemoryRouter>
  );
};

IncidentsByError.parameters = {
  msw: [
    rest.get('/api/incidents/byError', (_, res, ctx) => {
      return res(ctx.json(incidentsByError));
    }),
  ],
};

IncidentsByError.storyName = 'IncidentsByError - Success';

const IncidentsByErrorLoading: Story = () => {
  return (
    <MemoryRouter>
      <Container>
        <IncidentsByErrorComponent />
      </Container>
    </MemoryRouter>
  );
};

IncidentsByErrorLoading.storyName = 'IncidentsByError - Loading';
IncidentsByErrorLoading.parameters = {
  msw: [
    rest.get('/api/incidents/byError', (_, res, ctx) => {
      return res(ctx.delay('infinite'), ctx.json({}));
    }),
  ],
};
const IncidentsByErrorFailure: Story = () => {
  return (
    <MemoryRouter>
      <Container>
        <IncidentsByErrorComponent />
      </Container>
    </MemoryRouter>
  );
};

IncidentsByErrorFailure.storyName = 'IncidentsByError - Failure';
IncidentsByErrorFailure.parameters = {
  msw: [
    rest.get('/api/incidents/byError', (_, res, ctx) => {
      return res(ctx.status(500), ctx.json({}));
    }),
  ],
};

export {IncidentsByError, IncidentsByErrorLoading, IncidentsByErrorFailure};
