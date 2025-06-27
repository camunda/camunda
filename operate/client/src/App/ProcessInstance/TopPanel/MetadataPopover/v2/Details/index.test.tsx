/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {screen} from 'modules/testing-library';
import {V2InstanceMetadata, V2MetaDataDto} from '../types';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {baseMetaData, renderDetails} from './mocks';

describe('MetadataPopover v2 <Details />', () => {
  beforeEach(() => {
    mockFetchProcessDefinitionXml().withSuccess('');
  });

  it('renders element instance details', () => {
    renderDetails();
    expect(screen.getByText('Details')).toBeInTheDocument();
    expect(screen.getByText('Element Instance Key')).toBeInTheDocument();
    expect(screen.getByText('123456789')).toBeInTheDocument();
  });

  it('displays job retries when available', () => {
    renderDetails();
    expect(screen.getByText('Retries Left')).toBeInTheDocument();
    expect(screen.getByTestId('retries-left-count')).toHaveTextContent('3');
  });

  it('hides job retries when null', () => {
    const meta = {
      ...baseMetaData,
      instanceMetadata: {
        ...baseMetaData.instanceMetadata!,
        jobRetries: null,
      },
    };
    renderDetails(meta);
    expect(screen.queryByText('Retries Left')).not.toBeInTheDocument();
  });

  it('shows metadata dialog when "Show more metadata" is clicked', async () => {
    const {user} = renderDetails();
    await user.click(screen.getByRole('button', {name: 'Show more metadata'}));
    expect(
      screen.getByText(/Element "Task_1" 123456789 Metadata/),
    ).toBeInTheDocument();
  });

  it('handles null instance metadata gracefully', () => {
    const meta: V2MetaDataDto = {
      flowNodeInstanceId: null,
      flowNodeId: null,
      flowNodeType: null,
      instanceCount: null,
      instanceMetadata: null,
      incident: null,
      incidentCount: 0,
    };
    renderDetails(meta);
    expect(screen.getByText('Element Instance Key')).toBeInTheDocument();
    expect(screen.getByText('Execution Duration')).toBeInTheDocument();
  });

  it('shows called process instance info for call activities', () => {
    renderDetails();
    expect(screen.getByText('Details')).toBeInTheDocument();
  });

  it('renders Tasklist link for user tasks when configured', () => {
    window.clientConfig = {tasklistUrl: 'https://tasklist.example.com'};

    const meta = {
      ...baseMetaData,
      instanceMetadata: {
        ...baseMetaData.instanceMetadata!,
        type: 'USER_TASK' as V2InstanceMetadata['type'],
      },
    };
    renderDetails(meta);

    const link = screen.getByRole('link', {name: 'Open Tasklist'});
    expect(link).toBeInTheDocument();
    expect(link).toHaveAttribute('href', 'https://tasklist.example.com');
  });

  it('does not render Tasklist link for non-user tasks', () => {
    window.clientConfig = {tasklistUrl: 'https://tasklist.example.com'};
    renderDetails();
    expect(
      screen.queryByRole('link', {name: 'Open Tasklist'}),
    ).not.toBeInTheDocument();
  });

  it('displays execution duration info', () => {
    renderDetails();
    expect(screen.getByText('Execution Duration')).toBeInTheDocument();
  });
});
