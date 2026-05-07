/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import * as React from 'react';

import {Column as ShadcnColumn} from './column.shadcn';

import type {ColumnProps as CarbonColumnProps} from '@carbon/react';

export type ColumnProps<E extends React.ElementType = 'div'> =
  CarbonColumnProps<E>;

function Column<E extends React.ElementType = 'div'>(props: ColumnProps<E>) {
  // Carbon supports an `xlg` breakpoint alias; map it onto the primitive's
  // `xl` so existing call sites keep their column sizing.
  const {xlg, ...rest} = props as ColumnProps<E> & {
    xlg?: React.ComponentProps<typeof ShadcnColumn>['xl'];
  };

  const merged = {
    ...(rest as React.ComponentProps<typeof ShadcnColumn>),
    xl:
      (rest as {xl?: React.ComponentProps<typeof ShadcnColumn>['xl']}).xl ??
      xlg,
  };

  return <ShadcnColumn {...merged} />;
}

export {Column};
