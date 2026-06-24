/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {AgentInstanceStatus} from '@camunda/camunda-api-zod-schemas/8.10';
import {AgentStatusOverlay} from './index';
import {render, screen} from 'modules/testing-library';

describe('AgentStatusOverlay', () => {
  it.each<[AgentInstanceStatus, string]>([
    ['INITIALIZING', 'Starting...'],
    ['TOOL_DISCOVERY', 'Discovering tools...'],
    ['THINKING', 'Thinking...'],
    ['TOOL_CALLING', 'Calling tools...'],
  ])(
    'should render a status label for the active %s status',
    (status, label) => {
      render(<AgentStatusOverlay container={document.body} status={status} />);

      expect(
        screen.getByTestId(`agent-status-overlay-${status}`),
      ).toHaveTextContent(label);
    },
  );

  it.each<AgentInstanceStatus>(['COMPLETED', 'IDLE'])(
    'should render nothing for the non-active %s status',
    (status) => {
      render(<AgentStatusOverlay container={document.body} status={status} />);

      expect(
        screen.queryByTestId(`agent-status-overlay-${status}`),
      ).not.toBeInTheDocument();
    },
  );
});
