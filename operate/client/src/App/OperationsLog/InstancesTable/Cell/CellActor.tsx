/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

<<<<<<< HEAD
import AiAgentIcon from 'modules/components/Icon/ai-agent.svg?react';
import type {
  AuditLog,
  AuditLogActorType,
} from '@camunda/camunda-api-zod-schemas/8.9/audit-log';
import {AuthorTooltip, OperationLogName, TooltipCodeSnippet} from '../styled';
import {useMemo} from 'react';
import {spaceAndCapitalize} from 'modules/utils/spaceAndCapitalize';
import {Tooltip} from '@carbon/react';
import {getActorIcon} from 'modules/utils/operationsLog';
=======

import AiAgentIcon from 'modules/components/Icon/ai-agent.svg?react'
import type {AuditLog} from '@camunda/camunda-api-zod-schemas/8.9/audit-log';
import {OperationLogName, ActorTooltip} from '../styled';
import {User, Api} from '@carbon/react/icons';
import {useMemo} from 'react';
import {spaceAndCapitalize} from 'modules/utils/spaceAndCapitalize.ts';
import {Tooltip, CodeSnippet} from '@carbon/react';
>>>>>>> 2e5c1575 (feat: add tooltip component to actor cell)

type Props = {
  item: AuditLog;
};

const CellActor: React.FC<Props> = ({item}) => {
<<<<<<< HEAD
  const ActorIcon = useMemo(() => getActorIcon(item), [item]);

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
=======
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
>>>>>>> 2e5c1575 (feat: add tooltip component to actor cell)

  return item.actorId ? (
    <OperationLogName>
      {ActorIcon && (
<<<<<<< HEAD
        <Tooltip
          align="bottom-left"
          description={getTooltipActorContent(item.actorType)}
        >
=======
        <Tooltip align="bottom-left" description={TooltipActorContent}>
>>>>>>> 2e5c1575 (feat: add tooltip component to actor cell)
          <ActorIcon />
        </Tooltip>
      )}
      {item.agentElementId && (
<<<<<<< HEAD
        <Tooltip
          align="bottom-left"
          description={getTooltipActorContent('AGENT')}
        >
=======
        <Tooltip align="bottom-left" description={TooltipActorContent}>
>>>>>>> 2e5c1575 (feat: add tooltip component to actor cell)
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
