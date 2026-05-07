/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import * as React from 'react';

import {
  TableExpandHeader as ShadcnTableExpandHeader,
  TableExpandRow as ShadcnTableExpandRow,
  TableExpandedRow as ShadcnTableExpandedRow,
} from './table-expand.shadcn';

import {warnDroppedProps} from '../../../lib/utils';

import type {
  TableExpandHeaderProps as CarbonTableExpandHeaderProps,
  TableExpandRowProps as CarbonTableExpandRowProps,
  TableExpandedRowProps as CarbonTableExpandedRowProps,
} from '@carbon/react';

export type TableExpandHeaderProps = CarbonTableExpandHeaderProps;
export type TableExpandRowProps = CarbonTableExpandRowProps;
export type TableExpandedRowProps = CarbonTableExpandedRowProps;

function TableExpandHeader(props: TableExpandHeaderProps) {
  const {
    children,
    className,
    isExpanded,
    onExpand,
    enableToggle,
    enableExpando,
    expandIconDescription,
    'aria-label': ariaLabel,
    ariaLabel: deprecatedAriaLabel,
    'aria-controls': ariaControls,
    id,
    ...rest
  } = props as TableExpandHeaderProps & {
    children?: React.ReactNode;
    className?: string;
    isExpanded?: boolean;
    onExpand?: React.MouseEventHandler<HTMLButtonElement>;
    enableToggle?: boolean;
    enableExpando?: boolean;
    expandIconDescription?: string;
    'aria-label'?: string;
    ariaLabel?: string;
    'aria-controls'?: string;
    id?: string;
  };

  warnDroppedProps('TableExpandHeader', {
    ariaLabel: deprecatedAriaLabel,
    enableExpando,
  });

  return (
    <ShadcnTableExpandHeader
      className={className}
      isExpanded={isExpanded}
      onExpand={onExpand}
      enableToggle={enableToggle ?? enableExpando}
      expandIconDescription={expandIconDescription}
      aria-label={ariaLabel ?? deprecatedAriaLabel}
      aria-controls={ariaControls}
      id={id}
      {...(rest as React.HTMLAttributes<HTMLTableCellElement>)}
    >
      {children}
    </ShadcnTableExpandHeader>
  );
}

function TableExpandRow(props: TableExpandRowProps) {
  const {
    children,
    className,
    isExpanded,
    isSelected,
    onExpand,
    expandHeader,
    'aria-label': ariaLabel,
    ariaLabel: deprecatedAriaLabel,
    'aria-controls': ariaControls,
    ...rest
  } = props as TableExpandRowProps & {
    children?: React.ReactNode;
    className?: string;
    isExpanded?: boolean;
    isSelected?: boolean;
    onExpand: React.MouseEventHandler<HTMLButtonElement>;
    expandHeader?: string;
    'aria-label': string;
    ariaLabel?: string;
    'aria-controls'?: string;
  };

  warnDroppedProps('TableExpandRow', {ariaLabel: deprecatedAriaLabel});

  return (
    <ShadcnTableExpandRow
      className={className}
      isExpanded={isExpanded}
      isSelected={isSelected}
      onExpand={onExpand}
      expandHeader={expandHeader}
      aria-label={ariaLabel ?? deprecatedAriaLabel ?? ''}
      aria-controls={ariaControls}
      {...(rest as React.HTMLAttributes<HTMLTableRowElement>)}
    >
      {children}
    </ShadcnTableExpandRow>
  );
}

function TableExpandedRow(props: TableExpandedRowProps) {
  const {children, className, colSpan, ...rest} = props as TableExpandedRowProps & {
    children?: React.ReactNode;
    className?: string;
    colSpan: number;
  };

  return (
    <ShadcnTableExpandedRow
      className={className}
      colSpan={colSpan}
      {...(rest as React.HTMLAttributes<HTMLTableRowElement>)}
    >
      {children}
    </ShadcnTableExpandedRow>
  );
}

export {TableExpandHeader, TableExpandRow, TableExpandedRow};
