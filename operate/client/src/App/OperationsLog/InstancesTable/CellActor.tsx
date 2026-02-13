/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {AuditLog} from '@camunda/camunda-api-zod-schemas/8.9/audit-log';
import {Tooltip} from '@carbon/react';
import {
  UserAvatar,
  Application,
  Unknown,
  Bot,
} from '@carbon/react/icons';
import {OperationLogName} from './styled';

type Props = {
  item: AuditLog;
};

const getActorTypeIcon = (actorType: AuditLog['actorType']) => {
  switch (actorType) {
    case 'USER':
      return <UserAvatar aria-label="User" />;
    case 'CLIENT':
      return <Application aria-label="Client application" />;
    case 'ANONYMOUS':
    case 'UNKNOWN':
    default:
      return <Unknown aria-label="Unknown" />;
  }
};

const CellActor: React.FC<Props> = ({item}) => {
  return (
    <OperationLogName>
      {getActorTypeIcon(item.actorType)}
      {item.actorId}
      {item.agentElementId && (
        <Tooltip description={`Agent: ${item.agentElementId}`} align="bottom">
          <span style={{display: 'inline-flex', cursor: 'help'}}>
            <Bot aria-label="Agent" />
          </span>
        </Tooltip>
      )}
    </OperationLogName>
  );
};

export {CellActor};

