/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {type AuditLogResult} from '@camunda/camunda-api-zod-schemas/8.9/audit-log';
import {CheckmarkOutline, ErrorOutline} from './styled';

const stateIconsMap = {
  FAIL: ErrorOutline,
  SUCCESS: CheckmarkOutline,
} as const;

type Props = {
  state: AuditLogResult;
};

const OperationsLogStateIcon: React.FC<Props> = ({state}) => {
  const TargetComponent = stateIconsMap[state];
  return <TargetComponent data-testid={`${state}-icon`} size={20} />;
};

export {OperationsLogStateIcon};
