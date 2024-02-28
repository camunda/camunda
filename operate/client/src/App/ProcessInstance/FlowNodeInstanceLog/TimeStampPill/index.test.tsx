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
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {useEffect} from 'react';
import {mockFetchFlowNodeInstances} from 'modules/mocks/api/fetchFlowNodeInstances';

jest.mock('modules/utils/bpmn');

const Wrapper = ({children}: {children?: React.ReactNode}) => {
  mockFetchFlowNodeInstances().withSuccess({});
  mockFetchProcessXML().withSuccess('');

  useEffect(() => {
    flowNodeInstanceStore.fetchInstanceExecutionHistory('1');
    processInstanceDetailsDiagramStore.fetchProcessXml('1');
    return () => {
      flowNodeTimeStampStore.reset();
      processInstanceDetailsDiagramStore.reset();
      flowNodeInstanceStore.reset();
    };
  }, []);

  return <>{children}</>;
};

describe('TimeStampPill', () => {
  beforeEach(() => {
    mockFetchProcessXML().withSuccess('');
  });

  it('should render "Show" / "Hide" label', async () => {
    const {user} = render(<TimeStampPill />, {wrapper: Wrapper});

    expect(screen.getByText('Show End Date')).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.getByRole('switch', {name: 'Show End Date'})).toBeEnabled();
    });

    await user.click(screen.getByRole('switch', {name: 'Show End Date'}));

    expect(
      await screen.findByRole('switch', {name: 'Hide End Date'}),
    ).toBeInTheDocument();
  });

  it('should be disabled if diagram and instance execution history is not loaded', async () => {
    render(<TimeStampPill />, {wrapper: Wrapper});

    expect(screen.getByRole('switch')).toBeDisabled();

    await waitFor(() => expect(screen.getByRole('switch')).toBeEnabled());
  });
});
