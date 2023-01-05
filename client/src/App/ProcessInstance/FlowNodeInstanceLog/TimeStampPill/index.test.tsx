/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen, waitFor} from 'modules/testing-library';
import {TimeStampPill} from './index';
import {flowNodeTimeStampStore} from 'modules/stores/flowNodeTimeStamp';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {flowNodeInstanceStore} from 'modules/stores/flowNodeInstance';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';

jest.mock('modules/utils/bpmn');

describe('TimeStampPill', () => {
  beforeAll(() => {
    //@ts-ignore
    IS_REACT_ACT_ENVIRONMENT = false;
  });

  afterAll(() => {
    //@ts-ignore
    IS_REACT_ACT_ENVIRONMENT = true;
  });

  beforeEach(() => {
    mockFetchProcessXML().withSuccess('');
  });

  afterEach(() => {
    flowNodeTimeStampStore.reset();
    processInstanceDetailsDiagramStore.reset();
    flowNodeInstanceStore.reset();
  });

  it('should render "Show" / "Hide" label', async () => {
    render(<TimeStampPill />, {wrapper: ThemeProvider});

    expect(screen.getByText('Show End Date')).toBeInTheDocument();
    flowNodeTimeStampStore.toggleTimeStampVisibility();
    expect(await screen.findByText('Hide End Date')).toBeInTheDocument();
  });

  it('should be disabled if diagram and instance execution history is not loaded', async () => {
    render(<TimeStampPill />, {wrapper: ThemeProvider});

    expect(screen.getByRole('button')).toBeDisabled();
    await flowNodeInstanceStore.fetchInstanceExecutionHistory('1');
    await processInstanceDetailsDiagramStore.fetchProcessXml('1');
    await waitFor(() => expect(screen.getByRole('button')).toBeEnabled());
  });
});
