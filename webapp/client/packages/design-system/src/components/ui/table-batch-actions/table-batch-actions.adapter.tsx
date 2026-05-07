/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import * as React from 'react';

import {
  TableBatchActions as ShadcnTableBatchActions,
  TableBatchAction as ShadcnTableBatchAction,
} from './table-batch-actions.shadcn';

import {warnDroppedProps} from '../../../lib/utils';

import type {
  TableBatchActionsProps as CarbonTableBatchActionsProps,
  TableBatchActionProps as CarbonTableBatchActionProps,
} from '@carbon/react';

export type TableBatchActionsProps = CarbonTableBatchActionsProps;
export type TableBatchActionProps = CarbonTableBatchActionProps;

function TableBatchActions(props: TableBatchActionsProps) {
  const {
    children,
    className,
    shouldShowBatchActions,
    totalSelected,
    totalCount,
    onCancel,
    onSelectAll,
    translateWithId,
    ...rest
  } = props as TableBatchActionsProps & {
    children?: React.ReactNode;
    className?: string;
    shouldShowBatchActions?: boolean;
    totalSelected: number;
    totalCount?: number;
    onCancel: React.MouseEventHandler<HTMLButtonElement>;
    onSelectAll?: React.MouseEventHandler<HTMLButtonElement>;
    translateWithId?: (
      id: string,
      args?: {totalSelected?: number; totalCount?: number},
    ) => string;
  };

  const selectionLabel = translateWithId
    ? (n: number) =>
        translateWithId(
          n === 1
            ? 'carbon.table.batch.item.selected'
            : 'carbon.table.batch.items.selected',
          {totalSelected: n, totalCount},
        )
    : undefined;

  return (
    <ShadcnTableBatchActions
      className={className}
      shouldShowBatchActions={shouldShowBatchActions}
      totalSelected={totalSelected}
      totalCount={totalCount}
      onCancel={onCancel}
      onSelectAll={onSelectAll}
      selectionLabel={selectionLabel}
      {...(rest as React.HTMLAttributes<HTMLDivElement>)}
    >
      {children}
    </ShadcnTableBatchActions>
  );
}

function TableBatchAction(props: TableBatchActionProps) {
  const {
    children,
    className,
    renderIcon,
    iconDescription,
    hasIconOnly,
    ...rest
  } = props as TableBatchActionProps & {
    children?: React.ReactNode;
    className?: string;
    renderIcon?: React.ElementType;
    iconDescription?: string;
    hasIconOnly?: boolean;
  };

  warnDroppedProps('TableBatchAction', {});

  return (
    <ShadcnTableBatchAction
      className={className}
      renderIcon={renderIcon}
      iconDescription={iconDescription}
      hasIconOnly={hasIconOnly}
      {...(rest as React.ButtonHTMLAttributes<HTMLButtonElement>)}
    >
      {children}
    </ShadcnTableBatchAction>
  );
}

export {TableBatchActions, TableBatchAction};
