/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import * as React from 'react';

import {Skeleton} from '../skeleton/skeleton.shadcn';

import {cn} from '../../../lib/utils';

import type {SkeletonIconProps as CarbonSkeletonIconProps} from '@carbon/react';

export type SkeletonIconProps = CarbonSkeletonIconProps;

function SkeletonIcon(props: SkeletonIconProps) {
  const {className, ...rest} = props;

  return (
    <Skeleton
      className={cn('size-4', className)}
      {...(rest as React.ComponentProps<typeof Skeleton>)}
    />
  );
}

export {SkeletonIcon};
