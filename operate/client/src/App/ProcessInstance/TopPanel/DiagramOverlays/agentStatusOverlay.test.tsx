/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {AgentInstanceStatus} from '@camunda/camunda-api-zod-schemas/8.10';
import {AgentStatusOverlay} from './agentStatusOverlay';
import {render, screen} from 'modules/testing-library';
import type {DiagramOverlay} from './types';

const createOverlay = (
  status: AgentInstanceStatus,
  additionalActiveCount = 0,
): DiagramOverlay => ({
  container: document.body,
  elementId: 'Activity_agent',
  type: 'agentStatus',
  payload: {status, agentInstanceKey: 'agent-1', additionalActiveCount},
});

describe('agentStatusOverlay', () => {
  it.each<[AgentInstanceStatus, string]>([
    ['INITIALIZING', 'Starting...'],
    ['TOOL_DISCOVERY', 'Discovering tools...'],
    ['THINKING', 'Thinking...'],
    ['TOOL_CALLING', 'Calling tools...'],
  ])(
    'should render a status label for the active %s status',
    (status, label) => {
      render(<AgentStatusOverlay overlay={createOverlay(status)} />);

      expect(
        screen.getByTestId(`agent-status-overlay-${status}`),
      ).toHaveTextContent(label);
    },
  );

  it.each<AgentInstanceStatus>(['COMPLETED', 'IDLE'])(
    'should render nothing for the non-active %s status',
    (status) => {
      render(<AgentStatusOverlay overlay={createOverlay(status)} />);

      expect(
        screen.queryByTestId(`agent-status-overlay-${status}`),
      ).not.toBeInTheDocument();
    },
  );

  it('should append the count of other active agents on the element', () => {
    render(<AgentStatusOverlay overlay={createOverlay('THINKING', 3)} />);

    expect(
      screen.getByTestId('agent-status-overlay-THINKING'),
    ).toHaveTextContent('Thinking... + 3 more active');
  });

  it('should not append a count when no other agents are active', () => {
    render(<AgentStatusOverlay overlay={createOverlay('THINKING', 0)} />);

    const overlay = screen.getByTestId('agent-status-overlay-THINKING');
    expect(overlay).toHaveTextContent('Thinking...');
    expect(overlay).not.toHaveTextContent('more active');
  });
});
