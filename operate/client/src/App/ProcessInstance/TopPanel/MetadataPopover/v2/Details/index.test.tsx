/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {type V2MetaDataDto} from '../types';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {baseMetaData, TestWrapper} from './mocks';
import {Details} from './index';
import {getExecutionDuration} from '../../Details/getExecutionDuration';

describe('MetadataPopover <Details />', () => {
  beforeEach(() => {
    mockFetchProcessDefinitionXml().withSuccess('');
  });

  it('should render element instance details', () => {
    render(<Details metaData={baseMetaData} elementId="Task_1" />, {
      wrapper: TestWrapper,
    });

    expect(screen.getByText('Details')).toBeInTheDocument();
    expect(screen.getByText('Element Instance Key')).toBeInTheDocument();
    expect(screen.getByText('123456789')).toBeInTheDocument();
  });

  it('should display job retries when available', () => {
    render(<Details metaData={baseMetaData} elementId="Task_1" />, {
      wrapper: TestWrapper,
    });

    expect(screen.getByText('Retries Left')).toBeInTheDocument();
    expect(screen.getByTestId('retries-left-count')).toHaveTextContent('3');
  });

  it('should hide job retries when null', () => {
    const meta = {
      ...baseMetaData,
      instanceMetadata: {
        ...baseMetaData.instanceMetadata!,
        jobRetries: null,
      },
    };

    render(<Details metaData={meta} elementId="Task_1" />, {
      wrapper: TestWrapper,
    });

    expect(screen.queryByText('Retries Left')).not.toBeInTheDocument();
  });

  it('should show metadata dialog when "Show more metadata" is clicked', async () => {
    const {user} = render(
      <Details metaData={baseMetaData} elementId="Task_1" />,
      {wrapper: TestWrapper},
    );

    await user.click(screen.getByRole('button', {name: 'Show more metadata'}));
    expect(
      screen.getByText(/Element "Task_1" 123456789 Metadata/),
    ).toBeInTheDocument();
  });

  it('should handle null instance metadata gracefully', () => {
    const meta: V2MetaDataDto = {
      flowNodeInstanceId: null,
      flowNodeId: null,
      flowNodeType: null,
      instanceCount: null,
      instanceMetadata: null,
      incident: null,
      incidentCount: 0,
    };

    render(<Details metaData={meta} elementId="Task_1" />, {
      wrapper: TestWrapper,
    });

    expect(screen.getByText('Element Instance Key')).toBeInTheDocument();
    expect(screen.getByText('Execution Duration')).toBeInTheDocument();
    expect(screen.queryByText('Retries Left')).not.toBeInTheDocument();
    expect(
      screen.queryByText('Called Process Instance'),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText('Called Decision Instance'),
    ).not.toBeInTheDocument();
  });

  it('should render Tasklist link for user tasks when configured', () => {
    const tasklistUrl = 'https://tasklist.example.com';
    vi.stubGlobal('clientConfig', {tasklistUrl});

    const meta: V2MetaDataDto = {
      ...baseMetaData,
      instanceMetadata: {
        ...baseMetaData.instanceMetadata!,
        type: 'USER_TASK',
      },
    };

    render(<Details metaData={meta} elementId="Task_1" />, {
      wrapper: TestWrapper,
    });

    const link = screen.getByRole('link', {name: 'Open Tasklist'});
    expect(link).toBeInTheDocument();
    expect(link).toHaveAttribute('href', 'https://tasklist.example.com');
  });

  it('should not render Tasklist link for non-user tasks', () => {
    const tasklistUrl = 'https://tasklist.example.com';
    vi.stubGlobal('clientConfig', {tasklistUrl});

    render(<Details metaData={baseMetaData} elementId="Task_1" />, {
      wrapper: TestWrapper,
    });

    expect(
      screen.queryByRole('link', {name: 'Open Tasklist'}),
    ).not.toBeInTheDocument();
  });

  it('should display execution duration info', () => {
    render(<Details metaData={baseMetaData} elementId="Task_1" />, {
      wrapper: TestWrapper,
    });

    const calculatedExecutionDuration = getExecutionDuration(
      baseMetaData!.instanceMetadata!.startDate,
      baseMetaData!.instanceMetadata!.endDate,
    );

    expect(screen.getByText('Execution Duration')).toBeInTheDocument();
    expect(screen.getByText(calculatedExecutionDuration)).toBeInTheDocument();
  });
});
