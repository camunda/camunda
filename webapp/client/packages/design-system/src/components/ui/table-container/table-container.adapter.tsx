/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import * as React from 'react';

import {TableContainer as ShadcnTableContainer} from './table-container.shadcn';

import {warnDroppedProps} from '../../../lib/utils';

import type {TableContainerProps as CarbonTableContainerProps} from '@carbon/react';

export type TableContainerProps = CarbonTableContainerProps;

function TableContainer(props: TableContainerProps) {
  const {
    title,
    description,
    decorator,
    stickyHeader,
    useStaticWidth,
    aiEnabled,
    className,
    children,
    ...rest
  } = props as TableContainerProps & {
    title?: React.ReactNode;
    description?: React.ReactNode;
    decorator?: React.ReactNode;
    stickyHeader?: boolean;
    useStaticWidth?: boolean;
    aiEnabled?: boolean;
    className?: string;
    children?: React.ReactNode;
  };

  warnDroppedProps('TableContainer', {decorator, aiEnabled});

  return (
    <ShadcnTableContainer
      title={title}
      description={description}
      sticky={stickyHeader}
      fitContent={useStaticWidth}
      className={className}
      {...(rest as React.HTMLAttributes<HTMLDivElement>)}
    >
      {children}
    </ShadcnTableContainer>
  );
}

export {TableContainer};
