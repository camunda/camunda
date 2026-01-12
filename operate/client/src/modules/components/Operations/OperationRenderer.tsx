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
  useIcons?: boolean;
};

const OperationRenderer: React.FC<Props> = ({
  operation,
  processInstanceKey,
  useIcons = false,
}) => {
  const baseProps = {
    processInstanceKey,
    onExecute: operation.onExecute,
    disabled: operation.disabled,
    useIcons,
  };

  switch (operation.type) {
    case 'RESOLVE_INCIDENT':
      return <ResolveIncident {...baseProps} />;
    case 'MIGRATE_PROCESS_INSTANCE':
      return (
        <OperationItem
          type="MIGRATE_PROCESS_INSTANCE"
          onClick={operation.onExecute}
          title={operation.label || `Migrate Instance ${processInstanceKey}`}
          disabled={operation.disabled}
          size="sm"
          useIcons={useIcons}
        />
      );
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
          useIcons={useIcons}
        />
      );
    default:
      return null;
  }
};

export {OperationRenderer};
