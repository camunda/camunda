/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect} from 'react';
import {render, screen, waitFor} from 'modules/testing-library';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {ExecutionCountToggle} from './index';

jest.mock('modules/utils/bpmn');

const Wrapper = ({children}: {children?: React.ReactNode}) => {
  mockFetchProcessXML().withSuccess('');

  useEffect(() => {
    processInstanceDetailsDiagramStore.fetchProcessXml('1');
    return () => {
      processInstanceDetailsDiagramStore.reset();
    };
  }, []);

  return <>{children}</>;
};

describe('TimeStampPill', () => {
  beforeEach(() => {
    mockFetchProcessXML().withSuccess('');
  });

  it('should render "Show" / "Hide" label', async () => {
    const {user} = render(<ExecutionCountToggle />, {wrapper: Wrapper});

    await waitFor(() => {
      expect(screen.getByLabelText('Show Execution Count')).toBeEnabled();
    });

    await user.click(screen.getByLabelText('Show Execution Count'));

    expect(
      await screen.findByLabelText('Hide Execution Count'),
    ).toBeInTheDocument();

    await user.click(screen.getByLabelText('Hide Execution Count'));

    expect(
      await screen.findByLabelText('Show Execution Count'),
    ).toBeInTheDocument();
  });

  it('should be disabled if diagram is not loaded', async () => {
    render(<ExecutionCountToggle />, {wrapper: Wrapper});

    expect(screen.getByRole('switch')).toBeDisabled();

    await waitFor(() => expect(screen.getByRole('switch')).toBeEnabled());
  });
});
