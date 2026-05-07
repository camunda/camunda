/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * Carbon's `SkeletonText` renders one or more line-shaped placeholders;
 * `paragraph` toggles multi-line, `lineCount` controls how many, and
 * `heading` bumps line height. shadcn ships only the generic `Skeleton`,
 * so multi-line is composed here as N stacked Skeletons. `width` is a
 * Carbon-only inline-style hook with no shadcn equivalent — it's dropped
 * with a warning (consumers can pass className for sizing).
 */

import * as React from 'react';

import {Skeleton} from '../skeleton/skeleton.shadcn';

import {cn, warnDroppedProps} from '../../../lib/utils';

import type {SkeletonTextProps as CarbonSkeletonTextProps} from '@carbon/react';

export type SkeletonTextProps = CarbonSkeletonTextProps;

function SkeletonText(props: SkeletonTextProps) {
  const {className, heading, lineCount = 3, paragraph, width, ...rest} = props;

  warnDroppedProps('SkeletonText', {width});

  const lineClass = cn(heading ? 'h-6' : 'h-4', 'w-full', className);

  if (paragraph) {
    return (
      <div
        className="grid gap-2"
        {...(rest as React.HTMLAttributes<HTMLDivElement>)}
      >
        {Array.from({length: lineCount}).map((_, i) => (
          <Skeleton key={i} className={lineClass} />
        ))}
      </div>
    );
  }

  return (
    <Skeleton
      className={lineClass}
      {...(rest as React.ComponentProps<typeof Skeleton>)}
    />
  );
}

export {SkeletonText};
