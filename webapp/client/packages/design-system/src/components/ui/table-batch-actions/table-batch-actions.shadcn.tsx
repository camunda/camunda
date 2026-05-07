/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import * as React from 'react';

import {cn} from '../../../lib/utils';
import {Button} from '../button/button.shadcn';

type TableBatchActionsProps = React.HTMLAttributes<HTMLDivElement> & {
  shouldShowBatchActions?: boolean;
  totalSelected: number;
  totalCount?: number;
  onCancel: React.MouseEventHandler<HTMLButtonElement>;
  onSelectAll?: React.MouseEventHandler<HTMLButtonElement>;
  selectionLabel?: (totalSelected: number) => React.ReactNode;
};

function TableBatchActions({
  className,
  children,
  shouldShowBatchActions,
  totalSelected,
  totalCount,
  onCancel,
  onSelectAll,
  selectionLabel,
  ...rest
}: TableBatchActionsProps) {
  return (
    <div
      data-slot="table-batch-actions"
      data-active={shouldShowBatchActions || undefined}
      aria-hidden={!shouldShowBatchActions}
      className={cn(
        'flex h-12 w-full items-center justify-between bg-primary text-primary-foreground transition-transform duration-150',
        shouldShowBatchActions
          ? 'translate-y-0 opacity-100'
          : 'pointer-events-none -translate-y-1 opacity-0',
        className,
      )}
      {...rest}
    >
      <div data-slot="table-batch-actions-summary" className="flex items-center gap-3 px-3 text-sm">
        <span>
          {selectionLabel
            ? selectionLabel(totalSelected)
            : `${totalSelected} item${totalSelected === 1 ? '' : 's'} selected`}
        </span>
        {onSelectAll && totalCount != null && totalCount > totalSelected && (
          <Button
            variant="ghost"
            size="sm"
            onClick={onSelectAll}
            className="text-primary-foreground hover:bg-primary-foreground/10"
          >
            Select all ({totalCount})
          </Button>
        )}
      </div>
      <div data-slot="table-batch-actions-content" className="flex items-center gap-1 px-2">
        {children}
        <Button
          variant="ghost"
          size="sm"
          onClick={onCancel}
          className="text-primary-foreground hover:bg-primary-foreground/10"
        >
          Cancel
        </Button>
      </div>
    </div>
  );
}

type TableBatchActionProps = React.ButtonHTMLAttributes<HTMLButtonElement> & {
  renderIcon?: React.ElementType;
  iconDescription?: string;
  hasIconOnly?: boolean;
};

function TableBatchAction({
  className,
  renderIcon: Icon,
  iconDescription,
  hasIconOnly,
  children,
  ...rest
}: TableBatchActionProps) {
  return (
    <Button
      variant="ghost"
      size="sm"
      data-slot="table-batch-action"
      className={cn(
        'gap-2 text-primary-foreground hover:bg-primary-foreground/10',
        hasIconOnly && 'h-8 w-8 p-0',
        className,
      )}
      title={hasIconOnly ? iconDescription : undefined}
      aria-label={hasIconOnly ? iconDescription : rest['aria-label']}
      {...rest}
    >
      {!hasIconOnly && children}
      {Icon && <Icon aria-hidden="true" className="size-4" />}
    </Button>
  );
}

export {TableBatchActions, TableBatchAction};
export type {TableBatchActionsProps, TableBatchActionProps};
