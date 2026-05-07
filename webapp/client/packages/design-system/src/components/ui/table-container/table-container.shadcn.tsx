/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import * as React from 'react';

import {cn} from '../../../lib/utils';

type TableContainerProps = Omit<
  React.HTMLAttributes<HTMLDivElement>,
  'title'
> & {
  title?: React.ReactNode;
  description?: React.ReactNode;
  sticky?: boolean;
  fitContent?: boolean;
};

function TableContainer({
  className,
  title,
  description,
  sticky,
  fitContent,
  children,
  ...rest
}: TableContainerProps) {
  return (
    <div
      data-slot="table-container"
      data-sticky={sticky || undefined}
      className={cn(
        'flex flex-col bg-background',
        fitContent ? 'w-fit' : 'w-full',
        className,
      )}
      {...rest}
    >
      {(title || description) && (
        <div data-slot="table-container-header" className="px-4 pt-3 pb-2">
          {title && (
            <h4
              data-slot="table-container-title"
              className="text-base font-medium text-foreground"
            >
              {title}
            </h4>
          )}
          {description && (
            <p
              data-slot="table-container-description"
              className="mt-0.5 text-sm text-muted-foreground"
            >
              {description}
            </p>
          )}
        </div>
      )}
      {children}
    </div>
  );
}

export {TableContainer};
export type {TableContainerProps};
