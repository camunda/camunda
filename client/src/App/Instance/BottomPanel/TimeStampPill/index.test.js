/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {render, screen} from '@testing-library/react';
import {TimeStampPill} from './index';
import {flowNodeTimeStamp} from 'modules/stores/flowNodeTimeStamp';
import {singleInstanceDiagram} from 'modules/stores/singleInstanceDiagram';
import {flowNodeInstance} from 'modules/stores/flowNodeInstance';

jest.mock('modules/utils/bpmn');
jest.mock('modules/api/diagram', () => ({
  fetchWorkflowXML: jest.fn().mockImplementation(() => ''),
}));
jest.mock('modules/api/activityInstances', () => ({
  fetchActivityInstancesTree: jest.fn().mockImplementation(() => ({})),
}));

describe('TimeStampPill', () => {
  afterEach(() => {
    flowNodeTimeStamp.reset();
    singleInstanceDiagram.reset();
    flowNodeInstance.reset();
  });

  it('should render "Show" / "Hide" label', () => {
    render(<TimeStampPill />);

    expect(screen.getByText('Show End Time')).toBeInTheDocument();
    flowNodeTimeStamp.toggleTimeStampVisibility();
    expect(screen.getByText('Hide End Time')).toBeInTheDocument();
  });

  it('should be disabled if diagram and instance execution history is not loaded', async () => {
    render(<TimeStampPill />);

    expect(screen.getByRole('button')).toBeDisabled();
    await flowNodeInstance.fetchInstanceExecutionHistory(1);
    await singleInstanceDiagram.fetchWorkflowXml(1);
    expect(screen.getByRole('button')).toBeEnabled();
  });
});
