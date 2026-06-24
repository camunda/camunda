/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import AiAgentIcon from 'modules/components/Icon/ai-agent.svg?react';
import McpIcon from 'modules/components/Icon/mcp.svg?react';
import type {
  AuditLog,
  AuditLogActorType,
} from '@camunda/camunda-api-zod-schemas/8.10/audit-log';
import {AuthorTooltip, OperationLogName, TooltipCodeSnippet} from '../styled';
import {spaceAndCapitalize} from 'modules/utils/spaceAndCapitalize';
import {Tooltip} from '@carbon/react';
import {ActorIcon} from 'modules/utils/operationsLog/ActorIcon';
import {hasActorIcon} from 'modules/utils/operationsLog';

type Props = {
  item: AuditLog;
};

const CellActor: React.FC<Props> = ({item}) => {
  const getTooltipActorContent = (actor: AuditLogActorType | 'AGENT') => {
    const label =
      actor !== 'AGENT'
        ? spaceAndCapitalize(actor)
        : `AI Agent on behalf of ${item.actorType.toLowerCase()}`;
    return (
      <AuthorTooltip>
        <span>{label}</span>
        <TooltipCodeSnippet wrapText>
          {actor === 'AGENT' ? item.agentElementId : item.actorId}
        </TooltipCodeSnippet>
      </AuthorTooltip>
    );
  };

  return item.actorId ? (
    <OperationLogName>
      {hasActorIcon(item) && (
        <Tooltip
          autoAlign
          align="bottom-start"
          description={getTooltipActorContent(item.actorType)}
        >
          <ActorIcon auditLog={item} />
        </Tooltip>
      )}
      {item.agentElementId && (
        <Tooltip
          autoAlign
          align="bottom-start"
          description={getTooltipActorContent('AGENT')}
        >
          <AiAgentIcon />
        </Tooltip>
      )}
      {item.inboundChannelType === 'MCP' && (
        <Tooltip
          autoAlign
          align="bottom-start"
          description={
            <AuthorTooltip>
              <span>MCP tool call</span>
              {item.inboundChannelToolName && (
                <TooltipCodeSnippet wrapText>
                  {item.inboundChannelToolName}
                </TooltipCodeSnippet>
              )}
            </AuthorTooltip>
          }
        >
          <McpIcon />
        </Tooltip>
      )}
      {item.actorId}
    </OperationLogName>
  ) : (
    '-'
  );
};

export {CellActor};
