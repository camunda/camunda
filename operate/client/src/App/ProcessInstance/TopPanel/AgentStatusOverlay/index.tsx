/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createPortal} from 'react-dom';
import type {AgentInstanceStatus} from '@camunda/camunda-api-zod-schemas/8.10';
import {AgentStatus, AgentStatusContainer} from './styled';

const AGENT_STATUS_LABELS: Record<AgentInstanceStatus, string | null> = {
  INITIALIZING: 'Starting...',
  TOOL_DISCOVERY: 'Discovering tools...',
  THINKING: 'Thinking...',
  TOOL_CALLING: 'Calling tools...',
  IDLE: null,
  COMPLETED: null,
};

type Props = {
  container: HTMLElement;
  status: AgentInstanceStatus;
};

const AgentStatusOverlay: React.FC<Props> = ({container, status}) => {
  const label = AGENT_STATUS_LABELS[status];
  if (label === null) {
    return null;
  }

  return createPortal(
    <AgentStatusContainer>
      <AgentStatus data-testid={`agent-status-overlay-${status}`}>
        {label}
      </AgentStatus>
    </AgentStatusContainer>,
    container,
  );
};

export {AgentStatusOverlay};
