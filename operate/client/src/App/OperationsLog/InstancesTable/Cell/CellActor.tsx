/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import AiAgentIcon from 'modules/components/Icon/ai-agent.svg?react';
import type {AuditLog} from '@camunda/camunda-api-zod-schemas/8.9/audit-log';
import {AuthorTooltip, OperationLogName} from '../styled';
import {useMemo} from 'react';
import {spaceAndCapitalize} from 'modules/utils/spaceAndCapitalize.ts';
import {Tooltip} from '@carbon/react';
import {getActorIcon} from 'modules/utils/operationsLog';
import {Snippet} from 'modules/components/Snippet';

type Props = {
  item: AuditLog;
};

const CellActor: React.FC<Props> = ({item}) => {
  const ActorIcon = useMemo(() => getActorIcon(item), [item]);

  const getTooltipActorContent = (actor: AuditLog['actorType'] | 'AGENT') => {
    const label =
      actor !== 'AGENT'
        ? spaceAndCapitalize(actor)
        : `AI Agent on behalf of ${item.actorType.toLowerCase()}`;
    return (
      <AuthorTooltip>
        <span>{label}</span>
        <Snippet theme="dark" hideCopyButton wrapText>
          {actor === 'AGENT' ? item.agentElementId : item.actorId}
        </Snippet>
      </AuthorTooltip>
    );
  };

  return item.actorId ? (
    <OperationLogName>
      {ActorIcon && (
        <Tooltip
          align="bottom-left"
          description={getTooltipActorContent(item.actorType)}
        >
          <ActorIcon />
        </Tooltip>
      )}
      {item.agentElementId && (
        <Tooltip
          align="bottom-left"
          description={getTooltipActorContent('AGENT')}
        >
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
