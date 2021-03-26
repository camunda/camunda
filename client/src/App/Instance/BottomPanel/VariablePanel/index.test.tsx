/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {VariablePanel} from './index';
import {render, screen} from '@testing-library/react';
import {FAILED_PLACEHOLDER, MULTI_SCOPE_PLACEHOLDER} from './constants';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {variablesStore} from 'modules/stores/variables';
import {currentInstanceStore} from 'modules/stores/currentInstance';
import {MemoryRouter, Route} from 'react-router-dom';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';

jest.mock('../Variables', () => {
  return {
    __esModule: true,
    default: () => {
      return <div>{'Variables'}</div>;
    },
  };
});

type Props = {
  children?: React.ReactNode;
};

const Wrapper: React.FC<Props> = ({children}) => {
  return (
    <ThemeProvider>
      <MemoryRouter initialEntries={['/instances/1']}>
        <Route path="/instances/:processInstanceId">{children} </Route>
      </MemoryRouter>
    </ThemeProvider>
  );
};

describe('VariablePanel', () => {
  beforeEach(() => {
    mockServer.use(
      rest.get(
        '/api/process-instances/invalid_instance/variables',
        (_, res, ctx) => res.once(ctx.json({}), ctx.status(500))
      ),
      rest.get('/api/process-instances/:instanceId/variables', (_, res, ctx) =>
        res.once(ctx.json([]))
      )
    );

    currentInstanceStore.setCurrentInstance({
      id: 'instance_id',
      state: 'ACTIVE',
    });
  });

  afterEach(() => {
    flowNodeSelectionStore.reset();
  });

  it('should show multiple scope placeholder when multiple nodes are selected', () => {
    flowNodeSelectionStore.setSelection({
      flowNodeId: '1',
    });
    render(<VariablePanel />, {wrapper: Wrapper});

    expect(screen.getByText(MULTI_SCOPE_PLACEHOLDER)).toBeInTheDocument();
  });

  it('should show failed placeholder when variables could not be fetched', async () => {
    mockServer.use(
      rest.get(
        '/api/process-instances/invalid_instance/variables',
        (_, res, ctx) => res.once(ctx.json({}), ctx.status(500))
      ),
      rest.get('/api/process-instances/invalid_instance/variables', (_, res) =>
        res.networkError('A network error')
      )
    );

    const {unmount} = render(<VariablePanel />, {wrapper: Wrapper});
    variablesStore.fetchVariables('invalid_instance');

    expect(await screen.findByText(FAILED_PLACEHOLDER)).toBeInTheDocument();

    unmount();

    render(<VariablePanel />, {wrapper: Wrapper});
    variablesStore.fetchVariables('invalid_instance');

    expect(await screen.findByText(FAILED_PLACEHOLDER)).toBeInTheDocument();
  });

  it('should render variables', async () => {
    render(<VariablePanel />, {wrapper: Wrapper});
    await variablesStore.fetchVariables('1');

    expect(screen.getByText('Variables')).toBeInTheDocument();
  });
});
