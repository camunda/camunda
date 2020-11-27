/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {
  render,
  screen,
  within,
  waitForElementToBeRemoved,
} from '@testing-library/react';

import {CollapsablePanelProvider} from 'modules/contexts/CollapsablePanelContext';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import OperationsPanel from './index';
import * as CONSTANTS from './constants';
import {mockOperationFinished, mockOperationRunning} from './index.setup';
import {rest} from 'msw';
import {mockServer} from 'modules/mockServer';

type Props = {
  children?: React.ReactNode;
};

const Wrapper = ({children}: Props) => {
  return (
    <ThemeProvider>
      <CollapsablePanelProvider>{children}</CollapsablePanelProvider>
    </ThemeProvider>
  );
};

describe('OperationsPanel', () => {
  it('should display empty panel on mount', async () => {
    mockServer.use(
      rest.post('/api/batch-operations', (_, res, ctx) =>
        res.once(ctx.json([]))
      )
    );

    render(<OperationsPanel />, {wrapper: Wrapper});

    await waitForElementToBeRemoved(screen.getByTestId('skeleton'));

    expect(screen.getByText(CONSTANTS.EMPTY_MESSAGE)).toBeInTheDocument();
  });

  it('should render skeleton when loading', async () => {
    mockServer.use(
      rest.post('/api/batch-operations', (_, res, ctx) =>
        res.once(ctx.json([]))
      )
    );
    render(<OperationsPanel />, {wrapper: Wrapper});

    expect(screen.getByTestId('skeleton')).toBeInTheDocument();

    await waitForElementToBeRemoved(screen.getByTestId('skeleton'));
  });

  it('should render operation entries', async () => {
    mockServer.use(
      rest.post('/api/batch-operations', (_, res, ctx) =>
        res.once(
          ctx.status(200),
          ctx.json([mockOperationRunning, mockOperationFinished])
        )
      )
    );
    render(<OperationsPanel />, {wrapper: Wrapper});

    await waitForElementToBeRemoved(screen.getByTestId('skeleton'));

    const withinFirstEntry = within(
      screen.getAllByTestId('operations-entry')[0]
    );
    const withinSecondEntry = within(
      screen.getAllByTestId('operations-entry')[1]
    );

    expect(
      withinFirstEntry.getByText(mockOperationRunning.id)
    ).toBeInTheDocument();
    expect(withinFirstEntry.getByText('Retry')).toBeInTheDocument();
    expect(
      withinFirstEntry.getByTestId('operation-retry-icon')
    ).toBeInTheDocument();

    expect(
      withinSecondEntry.getByText(mockOperationFinished.id)
    ).toBeInTheDocument();
    expect(withinSecondEntry.getByText('Cancel')).toBeInTheDocument();
    expect(
      withinSecondEntry.getByTestId('operation-cancel-icon')
    ).toBeInTheDocument();
  });
});
