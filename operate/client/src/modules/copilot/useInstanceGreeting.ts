/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useRef} from 'react';
import {useChatStore} from '@camunda/copilot-chat';
import {
  endpoints,
  type ProcessInstance,
} from '@camunda/camunda-api-zod-schemas/8.10';
import {requestWithThrow} from 'modules/request';
import {logger} from 'modules/logger';
import {useCurrentInstanceContext} from './useCurrentInstanceContext';

const fetchInstanceSummary = async (
  processInstanceId: string,
): Promise<ProcessInstance | null> => {
  const {response, error} = await requestWithThrow<ProcessInstance>({
    url: endpoints.getProcessInstance.getUrl({processInstanceKey: processInstanceId}),
    method: endpoints.getProcessInstance.method,
  });
  if (error !== null || response === null) {
    return null;
  }
  return response;
};

const formatRelativeTime = (iso: string): string => {
  const ts = Date.parse(iso);
  if (Number.isNaN(ts)) return 'recently';
  const minutes = Math.max(1, Math.round((Date.now() - ts) / 60_000));
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.round(hours / 24);
  return `${days}d ago`;
};

const formatGreeting = (summary: ProcessInstance): string => {
  const ago = formatRelativeTime(summary.startDate);
  const name = summary.processDefinitionName || 'this process';
  const baseFacts = `**Looking at instance \`${summary.processInstanceKey}\` of ${name}**`;
  if (summary.hasIncident) {
    return `${baseFacts} (started ${ago}, has active incidents). Want me to investigate?`;
  }
  if (summary.state === 'COMPLETED') {
    return `${baseFacts} (completed ${ago}). Want a summary of what happened?`;
  }
  if (summary.state === 'TERMINATED') {
    return `${baseFacts} (terminated ${ago}). Want details on why?`;
  }
  return `${baseFacts} (running since ${ago}). Want me to dig in?`;
};

const useInstanceGreeting = (): void => {
  const {processInstanceId} = useCurrentInstanceContext();
  const lastGreetedRef = useRef<string | null>(null);

  useEffect(() => {
    if (processInstanceId === null) return;
    if (lastGreetedRef.current === processInstanceId) return;

    const targetId = processInstanceId;
    fetchInstanceSummary(targetId)
      .then((summary) => {
        if (summary === null) return;
        // Guard against navigation that happened while the fetch was in-flight.
        if (lastGreetedRef.current === targetId) return;
        const greeting = formatGreeting(summary);
        const messageId =
          typeof crypto !== 'undefined' && 'randomUUID' in crypto
            ? crypto.randomUUID()
            : `greeting-${Date.now()}`;
        useChatStore.getState().addAssistantMessage(messageId);
        useChatStore.getState().setMessageContent(messageId, greeting);
        lastGreetedRef.current = targetId;
      })
      .catch((err) => {
        logger.error('Failed to render copilot instance greeting', err);
      });
  }, [processInstanceId]);
};

export {useInstanceGreeting};
