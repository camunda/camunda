/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {memo} from 'react';
import {Tag} from '@carbon/react';
import type {AgentTool} from '@camunda/camunda-api-zod-schemas/8.10';
import type {ProviderTool} from './useAgentToolConfiguration';
import {ToolList, ToolName, ToolDescription, EmptyHint} from './styled';

type ToolCategory = 'process' | 'camunda' | 'sandbox' | 'provider';
const CATEGORY_COLORS = {
  process: 'blue',
  camunda: 'warm-gray',
  sandbox: 'green',
  provider: 'purple',
} as const;

const getToolCategory = (tool: AgentTool): Exclude<ToolCategory, 'provider'> =>
  tool.name.startsWith('sandbox_')
    ? 'sandbox'
    : tool.name.startsWith('c8__') || tool.elementId === null
      ? 'camunda'
      : 'process';

type AvailableToolsProps = {
  tools: AgentTool[];
  providerTools?: ProviderTool[];
};

const AvailableTools: React.FC<AvailableToolsProps> = memo(
  function AvailableTools({tools, providerTools = []}) {
    if (tools.length === 0 && providerTools.length === 0) {
      return <EmptyHint>No tools available for the AI agent.</EmptyHint>;
    }

    const sortedTools = [
      ...tools.map((tool) => ({...tool, category: getToolCategory(tool)})),
      ...providerTools.map((tool) => ({
        ...tool,
        category: 'provider' as const,
      })),
    ].sort((a, b) => a.name.localeCompare(b.name));

    return (
      <ToolList>
        {sortedTools.map((tool) => (
          <li
            key={`${tool.category}-${tool.name}`}
            aria-labelledby={`agent-instance-tool-${tool.name}`}
          >
            <ToolName id={`agent-instance-tool-${tool.name}`}>
              {tool.name}
            </ToolName>
            <Tag
              size="sm"
              type={CATEGORY_COLORS[tool.category]}
              className={
                tool.category === 'camunda'
                  ? 'tool-category-camunda'
                  : undefined
              }
            >
              {tool.category}
            </Tag>
            {tool.description !== null && (
              <ToolDescription>{tool.description}</ToolDescription>
            )}
          </li>
        ))}
      </ToolList>
    );
  },
);

export {AvailableTools};
