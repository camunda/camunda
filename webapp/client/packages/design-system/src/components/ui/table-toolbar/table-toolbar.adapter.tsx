/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import * as React from 'react';

import {
  TableToolbar as ShadcnTableToolbar,
  TableToolbarContent as ShadcnTableToolbarContent,
} from './table-toolbar.shadcn';

import {warnDroppedProps} from '../../../lib/utils';

import type {TableToolbarProps as CarbonTableToolbarProps} from '@carbon/react';

export type TableToolbarProps = CarbonTableToolbarProps;

function TableToolbar(props: TableToolbarProps) {
  const {
    children,
    size,
    className,
    'aria-label': ariaLabel,
    ariaLabel: deprecatedAriaLabel,
    ...rest
  } = props as TableToolbarProps & {
    children?: React.ReactNode;
    size?: 'sm' | 'lg';
    className?: string;
    'aria-label'?: string;
    ariaLabel?: string;
  };

  warnDroppedProps('TableToolbar', {ariaLabel: deprecatedAriaLabel});

  return (
    <ShadcnTableToolbar
      size={size}
      className={className}
      aria-label={ariaLabel ?? deprecatedAriaLabel}
      {...(rest as React.HTMLAttributes<HTMLDivElement>)}
    >
      {children}
    </ShadcnTableToolbar>
  );
}

function TableToolbarContent(props: React.HTMLAttributes<HTMLDivElement>) {
  return <ShadcnTableToolbarContent {...props} />;
}

export {TableToolbar, TableToolbarContent};
