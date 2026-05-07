/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import * as React from 'react';

import {cn} from '../../../lib/utils';
import {useSectionLevel} from '../section/section.shadcn';

const TYPOGRAPHY: Record<1 | 2 | 3 | 4 | 5 | 6, string> = {
  1: 'text-4xl font-semibold tracking-tight',
  2: 'text-3xl font-semibold tracking-tight',
  3: 'text-2xl font-semibold tracking-tight',
  4: 'text-xl font-semibold tracking-tight',
  5: 'text-lg font-semibold tracking-tight',
  6: 'text-base font-semibold tracking-tight',
};

type HeadingProps = React.ComponentPropsWithoutRef<'h1'>;

function Heading({className, ...props}: HeadingProps) {
  const level = useSectionLevel();
  const Comp = `h${level}` as 'h1';
  return (
    <Comp
      data-slot="heading"
      data-level={level}
      className={cn(TYPOGRAPHY[level], className)}
      {...props}
    />
  );
}

export {Heading};
export type {HeadingProps};
