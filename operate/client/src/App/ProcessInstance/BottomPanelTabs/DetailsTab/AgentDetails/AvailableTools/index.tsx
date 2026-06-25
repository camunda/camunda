/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {AgentTool} from '@camunda/camunda-api-zod-schemas/8.10';
import {ToolList, ToolName, ToolDescription, EmptyHint} from './styled';

type AvailableToolsProps = {
  tools: AgentTool[];
};

const AvailableTools: React.FC<AvailableToolsProps> = ({tools}) => {
  if (tools.length === 0) {
    return <EmptyHint>No tools available for the AI agent.</EmptyHint>;
  }

  return (
    <ToolList>
      {tools.map((tool, i) => (
        <li key={i} aria-labelledby={`agent-instance-tool-${i}`}>
          <ToolName id={`agent-instance-tool-${i}`}>{tool.name}</ToolName>
          {tool.description !== null && (
            <ToolDescription>{tool.description}</ToolDescription>
          )}
        </li>
      ))}
    </ToolList>
  );
};

export {AvailableTools};
