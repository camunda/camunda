/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Button} from '@carbon/react';
import {OperationItem} from 'modules/components/OperationItem';
import {Cancel} from './Cancel';
import {Delete} from './Delete';
import {ResolveIncident} from './ResolveIncident';
import {Resume} from './Resume';
import {Suspend} from './Suspend';
import {OperationPlaceholder} from './styled';
import type {OperationConfig} from './types';

type Props = {
  operation: OperationConfig | null;
  processInstanceKey: string;
};

const OperationRenderer: React.FC<Props> = ({
  operation,
  processInstanceKey,
}) => {
  if (operation === null) {
    return (
      <OperationPlaceholder aria-hidden="true">
        <Button
          kind="ghost"
          hasIconOnly
          size="sm"
          iconDescription=""
          tabIndex={-1}
          disabled
        />
      </OperationPlaceholder>
    );
  }

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
    case 'SUSPEND_PROCESS_INSTANCE':
      return <Suspend {...baseProps} />;
    case 'RESUME_PROCESS_INSTANCE':
      return <Resume {...baseProps} />;
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
