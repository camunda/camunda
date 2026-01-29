/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState, useEffect} from 'react';
import {OperationItems} from 'modules/components/OperationItems';
import {InlineLoading, MenuButton, MenuItem} from '@carbon/react';
import {OperationsContainer} from './styled';
import {OperationRenderer} from './OperationRenderer';
import type {OperationConfig, OperationType} from './types';
import {BREAKPOINTS} from 'modules/constants';

const OPERATION_LABELS: Record<OperationType, string> = {
  RESOLVE_INCIDENT: 'Retry',
  MIGRATE_PROCESS_INSTANCE: 'Migrate',
  CANCEL_PROCESS_INSTANCE: 'Cancel',
  DELETE_PROCESS_INSTANCE: 'Delete',
  ENTER_MODIFICATION_MODE: 'Modify',
};

type Props = {
  operations: OperationConfig[];
  processInstanceKey: string;
  isLoading?: boolean;
  loadingMessage?: string;
  useIcons?: boolean;
};

const Operations: React.FC<Props> = ({
  operations,
  processInstanceKey,
  isLoading = false,
  loadingMessage,
  useIcons = false,
}) => {
  const [isTablet, setIsTablet] = useState(
    window.innerWidth < BREAKPOINTS.lg && window.innerWidth >= BREAKPOINTS.md,
  );

  useEffect(() => {
    const updateBreakpoint = () => {
      setIsTablet(
        window.innerWidth < BREAKPOINTS.lg &&
          window.innerWidth >= BREAKPOINTS.md,
      );
    };
    updateBreakpoint();
    window.addEventListener('resize', updateBreakpoint);
    return () => window.removeEventListener('resize', updateBreakpoint);
  }, []);

  return (
    <OperationsContainer orientation="horizontal">
      {isLoading ? (
        <InlineLoading
          data-testid="operation-spinner"
          title={
            loadingMessage ||
            `Instance ${processInstanceKey} has scheduled Operations`
          }
        />
      ) : null}
      {isTablet ? (
        <MenuButton
          kind="ghost"
          size="sm"
          label="Actions"
        >
          {operations.map((operation) => (
            <MenuItem
              key={operation.type}
              onClick={operation.onExecute}
              disabled={operation.disabled}
              label={operation.label || OPERATION_LABELS[operation.type]}
            />
          ))}
        </MenuButton>
      ) : (
        <OperationItems>
          {operations.map((operation) => (
            <OperationRenderer
              key={operation.type}
              operation={operation}
              processInstanceKey={processInstanceKey}
              useIcons={useIcons}
            />
          ))}
        </OperationItems>
      )}
    </OperationsContainer>
  );
};

export {Operations};
