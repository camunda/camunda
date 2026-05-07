/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import * as React from 'react';

import {
  Breadcrumb as ShadcnBreadcrumb,
  BreadcrumbItem as ShadcnBreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
} from './breadcrumb.shadcn';

import {warnDroppedProps} from '../../../lib/utils';

import type {
  BreadcrumbItemProps as CarbonBreadcrumbItemProps,
  BreadcrumbProps as CarbonBreadcrumbProps,
} from '@carbon/react';

export type BreadcrumbProps = CarbonBreadcrumbProps;
export type BreadcrumbItemProps = CarbonBreadcrumbItemProps;

function Breadcrumb(props: BreadcrumbProps) {
  const {
    children,
    className,
    noTrailingSlash,
    'aria-label': ariaLabel,
    ...rest
  } = props as BreadcrumbProps & {
    children?: React.ReactNode;
    className?: string;
    noTrailingSlash?: boolean;
    'aria-label'?: string;
  };

  warnDroppedProps('Breadcrumb', {noTrailingSlash});

  return (
    <ShadcnBreadcrumb
      aria-label={ariaLabel ?? 'breadcrumb'}
      className={className}
      {...(rest as React.ComponentProps<typeof ShadcnBreadcrumb>)}
    >
      <BreadcrumbList>{children}</BreadcrumbList>
    </ShadcnBreadcrumb>
  );
}

function BreadcrumbItem(props: BreadcrumbItemProps) {
  const {
    children,
    className,
    href,
    isCurrentPage,
    'aria-current': ariaCurrent,
    ...rest
  } = props as BreadcrumbItemProps & {
    children?: React.ReactNode;
    className?: string;
    href?: string;
    isCurrentPage?: boolean;
    'aria-current'?: React.AriaAttributes['aria-current'];
  };

  warnDroppedProps('BreadcrumbItem', {isCurrentPage});

  const isCurrent =
    isCurrentPage === true || ariaCurrent === 'page' || ariaCurrent === true;

  return (
    <ShadcnBreadcrumbItem
      className={className}
      {...(rest as React.ComponentProps<typeof ShadcnBreadcrumbItem>)}
    >
      {isCurrent ? (
        <BreadcrumbPage>{children}</BreadcrumbPage>
      ) : href !== undefined ? (
        <BreadcrumbLink href={href}>{children}</BreadcrumbLink>
      ) : (
        children
      )}
    </ShadcnBreadcrumbItem>
  );
}

export {Breadcrumb, BreadcrumbItem};
