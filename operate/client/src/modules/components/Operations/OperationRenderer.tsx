/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {OperationItem} from 'modules/components/OperationItem';
import {Cancel} from './Cancel';
import {Delete} from './Delete';
import {ResolveIncident} from './ResolveIncident';
import type {OperationConfig} from './types';

type Props = {
  operation: OperationConfig;
  processInstanceKey: string;
};

const OperationRenderer: React.FC<Props> = ({
  operation,
  processInstanceKey,
}) => {
  const baseProps = {
    processInstanceKey,
    onExecute: operation.onExecute,
    disabled: operation.disabled,
  };

  switch (operation.type) {
    case 'RESOLVE_INCIDENT':
      return <ResolveIncident {...baseProps} />;
    case 'CANCEL_PROCESS_INSTANCE':
      return <Cancel {...baseProps} />;
    case 'DELETE_PROCESS_INSTANCE':
      return <Delete {...baseProps} />;
    case 'ENTER_MODIFICATION_MODE':
      return (
        <OperationItem
          type="ENTER_MODIFICATION_MODE"
          onClick={operation.onExecute}
          title={operation.label || `Modify Instance ${processInstanceKey}`}
          disabled={operation.disabled}
          size="sm"
        />
      );
    default:
      return null;
  }
};

export {OperationRenderer};
