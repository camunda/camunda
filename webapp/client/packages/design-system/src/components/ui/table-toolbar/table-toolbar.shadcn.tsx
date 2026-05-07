/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import * as React from 'react';

import {cn} from '../../../lib/utils';

type TableToolbarSize = 'sm' | 'lg';

type TableToolbarProps = React.HTMLAttributes<HTMLDivElement> & {
  size?: TableToolbarSize;
};

function TableToolbar({className, size, children, ...rest}: TableToolbarProps) {
  return (
    <section
      data-slot="table-toolbar"
      data-size={size}
      aria-label={rest['aria-label'] ?? 'data table toolbar'}
      className={cn(
        'flex w-full items-center justify-between border-b border-border',
        size === 'sm' ? 'h-10' : 'h-12',
        className,
      )}
      {...rest}
    >
      {children}
    </section>
  );
}

function TableToolbarContent({
  className,
  ...props
}: React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      data-slot="table-toolbar-content"
      className={cn('flex flex-1 items-center justify-end gap-2 px-2', className)}
      {...props}
    />
  );
}

export {TableToolbar, TableToolbarContent};
export type {TableToolbarProps, TableToolbarSize};
