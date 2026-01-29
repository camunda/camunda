/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {InlineLoading} from '@carbon/react';
import {useMemo} from 'react';
import {useAgentContextVariable} from 'modules/queries/agentContext/useAgentContextVariable';
import {parseAgentContext} from 'modules/agentContext/parseAgentContext';
import {buildTimelineModel} from 'modules/agentContext/buildTimelineModel';
import {AgentTimeline} from './timeline/AgentTimeline';
import {Container, ErrorState, Header, Subtitle} from './styled';
import {AgentContextHeader} from './context/AgentContextHeader';

type Props = {
  isVisible: boolean;
  processInstanceKey: string;
  scopeKey: string | null;
  isRunning: boolean;
  reloadToken?: number;
};

const POLLING_INTERVAL_MS = 2000;

const AgentContextTab: React.FC<Props> = ({
  isVisible,
  processInstanceKey,
  scopeKey,
  isRunning,
  reloadToken,
}) => {
  // Debug: confirm the tab is mounted and visibility state
  console.debug('[AI Agent] AgentContextTab render', {
    isVisible,
    processInstanceKey,
    scopeKey,
    isRunning,
  });

  const shouldPoll = isVisible && isRunning;

  const query = useAgentContextVariable({
    processInstanceKey,
    scopeKey,
    enabled: isVisible,
    refetchInterval: shouldPoll ? POLLING_INTERVAL_MS : false,
    reloadToken,
  });

  console.debug('[AI Agent] AgentContextTab query state', {
    isVisible,
    status: query.status,
    isFetching: query.isFetching,
    hasParsed: Boolean(query.data?.parsed),
    hasParseError: Boolean(query.data?.parseError),
  });

  const timeline = useMemo(() => {
    if (!query.data?.parsed) {
      return null;
    }

    const ctx = parseAgentContext(query.data.parsed);
    return {
      ctx,
      model: buildTimelineModel({agentContext: ctx, isRunning: shouldPoll}),
    };
  }, [query.data?.parsed, shouldPoll]);

  if (query.isLoading) {
    return (
      <Container>
        <Header>AI Agent</Header>
        <Subtitle>Loading agent context…</Subtitle>
        <InlineLoading description="Loading…" />
      </Container>
    );
  }

  if (query.isError) {
    return (
      <Container>
        <Header>AI Agent</Header>
        <ErrorState>Failed to load agent context.</ErrorState>
      </Container>
    );
  }

  if (query.data?.parseError) {
    return (
      <Container>
        <Header>AI Agent</Header>
        <ErrorState>
          Agent context is not valid JSON: {query.data.parseError.message}
        </ErrorState>
      </Container>
    );
  }

  if (!timeline) {
    return (
      <Container>
        <Header>AI Agent</Header>
        <Subtitle>No agent context available.</Subtitle>
      </Container>
    );
  }

  return (
    <Container>
      <AgentContextHeader agentContext={timeline.ctx} />
      <AgentTimeline model={timeline.model} />
    </Container>
  );
};

export {AgentContextTab};
