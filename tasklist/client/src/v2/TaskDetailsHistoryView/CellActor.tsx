/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {QueryUserTaskAuditLogsResponseBody} from '@camunda/camunda-api-zod-schemas/8.9';
import {Tooltip} from '@carbon/react';
import {UserAvatar, Application, Unknown, Bot} from '@carbon/react/icons';

type AuditLogItem = QueryUserTaskAuditLogsResponseBody['items'][number];

type Props = {
  item: AuditLogItem;
};

const getActorTypeIcon = (actorType: AuditLogItem['actorType']) => {
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

const actorCellStyle: React.CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  gap: 'var(--cds-spacing-02)',
};

const agentIconStyle: React.CSSProperties = {
  display: 'inline-flex',
  cursor: 'help',
};

const CellActor: React.FC<Props> = ({item}) => {
  return (
    <span style={actorCellStyle}>
      {getActorTypeIcon(item.actorType)}
      {item.actorId}
      {item.agentElementId && (
        <Tooltip description={`Agent: ${item.agentElementId}`} align="bottom">
          <span style={agentIconStyle}>
            <Bot aria-label="Agent" />
          </span>
        </Tooltip>
      )}
    </span>
  );
};

export {CellActor};


