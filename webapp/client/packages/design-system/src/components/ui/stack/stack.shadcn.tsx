/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import * as React from 'react';

import {cn} from '../../../lib/utils';

const GAP_STEP_CLASS: Record<number, string> = {
  1: 'gap-0.5',
  2: 'gap-1',
  3: 'gap-2',
  4: 'gap-3',
  5: 'gap-4',
  6: 'gap-6',
  7: 'gap-8',
  8: 'gap-10',
  9: 'gap-12',
  10: 'gap-16',
};

type StackOrientation = 'vertical' | 'horizontal';

type StackProps<E extends React.ElementType> = {
  as?: E;
  orientation?: StackOrientation;
  gap?: number | string;
} & Omit<React.ComponentPropsWithoutRef<E>, 'as'>;

function Stack<E extends React.ElementType = 'div'>({
  as,
  orientation = 'vertical',
  gap,
  className,
  style,
  ...props
}: StackProps<E>) {
  const Comp = (as ?? 'div') as React.ElementType;
  const stepClass =
    typeof gap === 'number' ? GAP_STEP_CLASS[gap] ?? '' : '';
  const customGap =
    typeof gap === 'string' ? {gap, ...style} : style;

  return (
    <Comp
      data-slot="stack"
      data-orientation={orientation}
      className={cn(
        'flex',
        orientation === 'vertical' ? 'flex-col' : 'flex-row',
        stepClass,
        className,
      )}
      style={customGap}
      {...props}
    />
  );
}

export {Stack};
export type {StackProps, StackOrientation};
