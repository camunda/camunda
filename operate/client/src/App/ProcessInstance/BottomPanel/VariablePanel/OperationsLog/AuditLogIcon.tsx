/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {auditLogResultSchema} from '@camunda/camunda-api-zod-schemas/8.9/audit-log';
import {CheckmarkFilled, ErrorFilled} from './styled.ts';
import type z from 'zod';

const stateIconsMap = {
  FAIL: ErrorFilled,
  SUCCESS: CheckmarkFilled,
} as const satisfies Record<Props['state'], unknown>;

type AuditLogResult = z.infer<typeof auditLogResultSchema>;

type Props = {
  state: AuditLogResult;
};

const AuditLogIcon: React.FC<Props> = ({state}) => {
  const TargetComponent = stateIconsMap[state];
  return <TargetComponent data-testid={`${state}-icon`} size={20} />;
};

export {AuditLogIcon};
