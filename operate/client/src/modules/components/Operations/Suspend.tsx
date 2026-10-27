/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.10';
import {OperationItem} from 'modules/components/OperationItem';

type Props = {
  processInstanceKey: ProcessInstance['processInstanceKey'];
  onExecute: () => void;
  disabled?: boolean;
  title?: string;
};

const Suspend: React.FC<Props> = ({
  onExecute,
  disabled = false,
  title = 'Suspend Instance',
}) => (
  <OperationItem
    type="SUSPEND_PROCESS_INSTANCE"
    onClick={onExecute}
    title={title}
    disabled={disabled}
    size="sm"
  />
);

export {Suspend};
