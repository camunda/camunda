/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect} from 'react';
import type {
  AgentInstanceHistoryItem,
  AgentInstanceStatus,
} from '@camunda/camunda-api-zod-schemas/8.10';
import {useAgentInstanceHistory} from 'modules/queries/agentInstances/useAgentInstanceHistory';
import {isActiveAgentInstanceStatus} from 'modules/queries/agentInstances/agentInstanceStatus';
import {
  getContentEvidence,
  isRecord,
} from '../ConversationMessage/EvidenceCard/evidence';

type ProviderTool = {name: string; description: string};

const getConfiguration = (history: AgentInstanceHistoryItem[]) =>
  history
    .filter((item) => item.role === 'CONFIGURATION')
    .sort((a, b) =>
      BigInt(a.historyItemKey) < BigInt(b.historyItemKey) ? 1 : -1,
    )
    .flatMap((item) => getContentEvidence(item.content))
    .find((evidence) => evidence.kind === 'configuration');

const getProviderTools = (
  history: AgentInstanceHistoryItem[],
): ProviderTool[] => {
  const configuration = getConfiguration(history);
  if (!configuration) {return [];}
  const tools = new Map<string, ProviderTool>();
  const serverTools = configuration['serverTools'];
  if (isRecord(serverTools) && Array.isArray(serverTools['configured'])) {
    for (const name of serverTools['configured']) {
      if (typeof name === 'string') {
        tools.set(name, {
          name,
          description:
            'Configured provider-run capability. It runs inside the model provider, not as a BPMN process tool. Configuration alone does not confirm execution.',
        });
      }
    }
  }
  const parsing = configuration['documentParsing'];
  if (isRecord(parsing) && typeof parsing['plugin'] === 'string') {
    const name = parsing['plugin'];
    const engine =
      typeof parsing['pdfEngine'] === 'string'
        ? ` PDF engine: ${parsing['pdfEngine']}.`
        : '';
    tools.set(name, {
      name,
      description: `Configured provider-side document parsing plugin: processes attached PDFs for the model, rather than executing a BPMN tool call.${engine} Configuration alone does not confirm execution.`,
    });
  }
  return Array.from(tools.values());
};

const useAgentToolConfiguration = (
  agentInstanceKey: string | undefined,
  agentInstanceStatus: AgentInstanceStatus | undefined,
) => {
  const {
    data,
    status,
    hasNextPage,
    fetchNextPage,
    isFetchingNextPage,
    isPlaceholderData,
  } = useAgentInstanceHistory(agentInstanceKey ?? '', {
    enabled: agentInstanceKey !== undefined,
    sortOrder: 'asc',
    enablePeriodicRefetch:
      agentInstanceStatus !== undefined &&
      isActiveAgentInstanceStatus(agentInstanceStatus),
  });
  useEffect(() => {
    if (hasNextPage && !isFetchingNextPage) {fetchNextPage();}
  }, [hasNextPage, isFetchingNextPage, fetchNextPage]);
  const history = isPlaceholderData
    ? []
    : (data?.pages.flatMap((page) => page.items) ?? []);
  const prompt = getConfiguration(history)?.['systemPrompt'];
  return {
    providerTools: getProviderTools(history),
    resourceKey:
      isRecord(prompt) && typeof prompt['resourceKey'] === 'string'
        ? prompt['resourceKey']
        : null,
    status,
  };
};

export {useAgentToolConfiguration, getProviderTools};
export type {ProviderTool};
