/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import * as React from 'react';
import {ChevronRightIcon} from 'lucide-react';

import {cn} from '../../../lib/utils';

type TableExpandHeaderProps = React.HTMLAttributes<HTMLTableCellElement> & {
  isExpanded?: boolean;
  onExpand?: React.MouseEventHandler<HTMLButtonElement>;
  enableToggle?: boolean;
  expandIconDescription?: string;
};

function TableExpandHeader({
  className,
  isExpanded,
  onExpand,
  enableToggle,
  expandIconDescription,
  children,
  'aria-label': ariaLabel,
  'aria-controls': ariaControls,
  id,
  ...rest
}: TableExpandHeaderProps) {
  return (
    <th
      data-slot="table-expand-header"
      className={cn('w-10 p-0 align-middle', className)}
      id={id}
      {...rest}
    >
      {enableToggle ? (
        <button
          type="button"
          aria-label={ariaLabel}
          aria-controls={ariaControls}
          aria-expanded={isExpanded}
          onClick={onExpand}
          className="inline-flex h-9 w-9 items-center justify-center rounded-sm text-muted-foreground hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        >
          <ChevronRightIcon
            aria-hidden="true"
            className={cn(
              'size-4 transition-transform',
              isExpanded && 'rotate-90',
            )}
          />
          {expandIconDescription && (
            <span className="sr-only">{expandIconDescription}</span>
          )}
        </button>
      ) : null}
      {children}
    </th>
  );
}

type TableExpandRowProps = Omit<
  React.HTMLAttributes<HTMLTableRowElement>,
  'onClick'
> & {
  isExpanded?: boolean;
  isSelected?: boolean;
  onExpand: React.MouseEventHandler<HTMLButtonElement>;
  expandHeader?: string;
  'aria-controls'?: string;
  'aria-label': string;
};

function TableExpandRow({
  className,
  isExpanded,
  isSelected,
  onExpand,
  expandHeader,
  'aria-controls': ariaControls,
  'aria-label': ariaLabel,
  children,
  ...rest
}: TableExpandRowProps) {
  return (
    <tr
      data-slot="table-expand-row"
      data-expanded={isExpanded || undefined}
      data-state={isSelected ? 'selected' : undefined}
      className={cn(
        'border-b transition-colors hover:bg-muted/50 data-[state=selected]:bg-muted',
        className,
      )}
      {...rest}
    >
      <td className="w-10 p-0 align-middle">
        <button
          type="button"
          aria-label={ariaLabel}
          aria-controls={ariaControls}
          aria-expanded={isExpanded}
          aria-describedby={expandHeader}
          onClick={onExpand}
          className="inline-flex h-9 w-9 items-center justify-center rounded-sm text-muted-foreground hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        >
          <ChevronRightIcon
            aria-hidden="true"
            className={cn(
              'size-4 transition-transform',
              isExpanded && 'rotate-90',
            )}
          />
        </button>
      </td>
      {children}
    </tr>
  );
}

type TableExpandedRowProps = React.HTMLAttributes<HTMLTableRowElement> & {
  colSpan: number;
};

function TableExpandedRow({
  className,
  colSpan,
  children,
  ...rest
}: TableExpandedRowProps) {
  return (
    <tr
      data-slot="table-expanded-row"
      className={cn('border-b bg-muted/40', className)}
      {...rest}
    >
      <td colSpan={colSpan} className="p-3">
        {children}
      </td>
    </tr>
  );
}

export {TableExpandHeader, TableExpandRow, TableExpandedRow};
export type {
  TableExpandHeaderProps,
  TableExpandRowProps,
  TableExpandedRowProps,
};
