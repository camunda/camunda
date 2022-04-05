/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {render, screen} from '@testing-library/react';
import {TimeStampPill} from './index';
import {flowNodeTimeStampStore} from 'modules/stores/flowNodeTimeStamp';
import {singleInstanceDiagramStore} from 'modules/stores/singleInstanceDiagram';
import {flowNodeInstanceStore} from 'modules/stores/flowNodeInstance';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {ThemeProvider} from 'modules/theme/ThemeProvider';

jest.mock('modules/utils/bpmn');

describe('TimeStampPill', () => {
  beforeEach(() => {
    mockServer.use(
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(''))
      ),
      rest.post('/api/activity-instances', (_, res, ctx) =>
        res.once(ctx.json({}))
      )
    );
  });

  afterEach(() => {
    flowNodeTimeStampStore.reset();
    singleInstanceDiagramStore.reset();
    flowNodeInstanceStore.reset();
  });

  it('should render "Show" / "Hide" label', () => {
    render(<TimeStampPill />, {wrapper: ThemeProvider});

    expect(screen.getByText('Show End Time')).toBeInTheDocument();
    flowNodeTimeStampStore.toggleTimeStampVisibility();
    expect(screen.getByText('Hide End Time')).toBeInTheDocument();
  });

  it('should be disabled if diagram and instance execution history is not loaded', async () => {
    render(<TimeStampPill />, {wrapper: ThemeProvider});

    expect(screen.getByRole('button')).toBeDisabled();
    await flowNodeInstanceStore.fetchInstanceExecutionHistory('1');
    await singleInstanceDiagramStore.fetchProcessXml('1');
    expect(screen.getByRole('button')).toBeEnabled();
  });
});
