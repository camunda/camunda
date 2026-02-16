/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */


import AiAgentIcon from 'modules/components/Icon/ai-agent.svg?react'
import type {AuditLog} from '@camunda/camunda-api-zod-schemas/8.9/audit-log';
import {OperationLogName, ActorTooltip} from '../styled';
import {User, Api} from '@carbon/react/icons';
import {useMemo} from 'react';
import {spaceAndCapitalize} from 'modules/utils/spaceAndCapitalize.ts';
import {Tooltip, CodeSnippet} from '@carbon/react';

type Props = {
  item: AuditLog;
};

const CellActor: React.FC<Props> = ({item}) => {
  const ActorIcon = useMemo(() => {
    switch(item.actorType) {
      case 'USER':
        return User
      case 'CLIENT':
        return Api
      default:
        return null
    }
  }, [item])

  const TooltipActorContent = (
    <ActorTooltip>
      <span>{spaceAndCapitalize(item.actorType)}</span>
      <CodeSnippet hideCopyButton wrapText>
        {item.agentElementId ? item.agentElementId : item.actorId}
      </CodeSnippet>
    </ActorTooltip>
  );

  return item.actorId ? (
    <OperationLogName>
      {ActorIcon && (
        <Tooltip align="bottom-left" description={TooltipActorContent}>
          <ActorIcon />
        </Tooltip>
      )}
      {item.agentElementId && (
        <Tooltip align="bottom-left" description={TooltipActorContent}>
          <AiAgentIcon />
        </Tooltip>
      )}
      {item.actorId}
    </OperationLogName>
  ) : (
    '-'
  );
};

export {CellActor};
