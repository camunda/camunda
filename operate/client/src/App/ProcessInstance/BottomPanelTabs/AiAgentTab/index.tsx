/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {useAgentData} from 'modules/contexts/agentData';
import {MOCK_AGENT_SUBPROCESS_KEY} from 'modules/mock-server/agentDemoData';
import {EmptyMessage} from 'modules/components/EmptyMessage';
import {AgentTimelinePanel} from './AgentTimelinePanel';
import {DefaultAgentDetail} from './AgentDetailPanel';
import {Container, TimelinePane, DetailPane} from './styled';

export type AgentTimelineSelection =
  | {type: 'iteration'; iterationNumber: number}
  | {type: 'tool'; iterationNumber: number; toolIndex: number}
  | null;

const AiAgentTab: React.FC = () => {
  const {agentData, isAgentInstance, getAgentDataForElement} = useAgentData();
  const [selection, setSelection] = useState<AgentTimelineSelection>(null);

  if (!isAgentInstance || !agentData) {
    return (
      <EmptyMessage message="No AI Agent data available for this process instance." />
    );
  }

  const agentElementData = getAgentDataForElement(MOCK_AGENT_SUBPROCESS_KEY);

  if (!agentElementData) {
    return (
      <EmptyMessage message="No AI Agent data found for the agent sub-process." />
    );
  }

  return (
    <Container data-testid="ai-agent-tab">
      <TimelinePane>
        <AgentTimelinePanel
          agentData={agentElementData}
          selection={selection}
          onSelect={setSelection}
        />
      </TimelinePane>
      <DetailPane>
        <DefaultAgentDetail agentData={agentElementData} />
      </DetailPane>
    </Container>
  );
};

export {AiAgentTab};
