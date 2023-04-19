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
import {useEffect} from 'react';
import {act} from 'react-dom/test-utils';

jest.mock('modules/utils/bpmn');

const Wrapper = ({children}: {children?: React.ReactNode}) => {
  useEffect(() => {
    return () => {
      flowNodeTimeStampStore.reset();
      processInstanceDetailsDiagramStore.reset();
      flowNodeInstanceStore.reset();
    };
  }, []);

  return <ThemeProvider>{children}</ThemeProvider>;
};

describe('TimeStampPill', () => {
  beforeEach(() => {
    mockFetchProcessXML().withSuccess('');
  });

  it('should render "Show" / "Hide" label', async () => {
    render(<TimeStampPill />, {wrapper: Wrapper});

    expect(screen.getByText('Show End Date')).toBeInTheDocument();

    act(() => {
      flowNodeTimeStampStore.toggleTimeStampVisibility();
    });

    expect(await screen.findByText('Hide End Date')).toBeInTheDocument();
  });

  it('should be disabled if diagram and instance execution history is not loaded', async () => {
    render(<TimeStampPill />, {wrapper: Wrapper});

    expect(screen.getByRole('button')).toBeDisabled();

    act(() => {
      flowNodeInstanceStore.fetchInstanceExecutionHistory('1');
      processInstanceDetailsDiagramStore.fetchProcessXml('1');
    });

    await waitFor(() => expect(screen.getByRole('button')).toBeEnabled());
  });
});
