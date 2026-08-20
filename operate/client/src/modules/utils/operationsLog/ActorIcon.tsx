/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Api, User} from '@carbon/react/icons';
import type {AuditLog} from '@camunda/camunda-api-zod-schemas/8.10/audit-log';

const ActorIcon: React.FC<{auditLog: AuditLog}> = ({auditLog}) => {
  switch (auditLog.actorType) {
    case 'USER':
      return <User />;
    case 'CLIENT':
      return <Api />;
    default:
      return null;
  }
};
export {ActorIcon};
