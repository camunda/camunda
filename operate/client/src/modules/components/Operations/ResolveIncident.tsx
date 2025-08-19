/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type ProcessInstance} from '@vzeta/camunda-api-zod-schemas/8.8';
import {OperationItem} from 'modules/components/OperationItem';
type Props = {
  processInstanceKey: ProcessInstance['processInstanceKey'];
  onExecute: () => void;
  disabled?: boolean;
};

const ResolveIncident: React.FC<Props> = ({
  processInstanceKey,
  onExecute,
  disabled = false,
}) => {
  return (
    <OperationItem
      type="RESOLVE_INCIDENT"
      onClick={onExecute}
      title={`Retry Instance ${processInstanceKey}`}
      disabled={disabled}
      size="sm"
    />
  );
};

export {ResolveIncident};
