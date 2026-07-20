/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {memo} from 'react';
import type {AgentTool} from '@camunda/camunda-api-zod-schemas/8.10';
import {ToolList, ToolName, ToolDescription, EmptyHint} from './styled';

type AvailableToolsProps = {
  tools: AgentTool[];
};

const AvailableTools: React.FC<AvailableToolsProps> = memo(
  function AvailableTools({tools}) {
    if (tools.length === 0) {
      return <EmptyHint>No tools available for the AI agent.</EmptyHint>;
    }

    const sortedTools = Array.from(tools).sort((a, b) =>
      a.name.localeCompare(b.name),
    );

    return (
      <ToolList>
        {sortedTools.map((tool) => (
          <li
            key={`${tool.name}`}
            aria-labelledby={`agent-instance-tool-${tool.name}`}
          >
            <ToolName id={`agent-instance-tool-${tool.name}`}>
              {tool.name}
            </ToolName>
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
