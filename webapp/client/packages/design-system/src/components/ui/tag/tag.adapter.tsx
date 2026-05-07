/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import * as React from 'react';

import {Badge} from '../badge/badge.shadcn';

import {warnDroppedProps} from '../../../lib/utils';

import type {TagProps as CarbonTagProps} from '@carbon/react';

export type TagProps<T extends React.ElementType = 'div'> = CarbonTagProps<T>;

type CarbonTagType =
  | 'red'
  | 'magenta'
  | 'purple'
  | 'blue'
  | 'cyan'
  | 'teal'
  | 'green'
  | 'gray'
  | 'cool-gray'
  | 'warm-gray'
  | 'high-contrast'
  | 'outline';

type BadgeVariant = NonNullable<React.ComponentProps<typeof Badge>['variant']>;

const TYPE_TO_VARIANT: Record<CarbonTagType, BadgeVariant> = {
  red: 'destructive',
  magenta: 'secondary',
  purple: 'secondary',
  blue: 'secondary',
  cyan: 'secondary',
  teal: 'secondary',
  green: 'secondary',
  gray: 'secondary',
  'cool-gray': 'secondary',
  'warm-gray': 'secondary',
  'high-contrast': 'default',
  outline: 'outline',
};

function Tag(props: TagProps<'div'>) {
  const {
    type,
    size,
    renderIcon: RenderIcon,
    disabled,
    filter,
    onClose,
    decorator,
    slug,
    title,
    children,
    className,
    ...rest
  } = props as TagProps<'div'> & {
    type?: CarbonTagType;
    size?: 'sm' | 'md' | 'lg';
    renderIcon?: React.ElementType;
    disabled?: boolean;
    filter?: boolean;
    onClose?: (event: React.MouseEvent<HTMLButtonElement>) => void;
    decorator?: React.ReactNode;
    slug?: React.ReactNode;
    title?: string;
    children?: React.ReactNode;
    className?: string;
  };

  warnDroppedProps('Tag', {
    size,
    disabled,
    filter,
    onClose,
    decorator,
    slug,
    title,
  });

  const variant: BadgeVariant = type ? TYPE_TO_VARIANT[type] : 'secondary';

  return (
    <Badge
      variant={variant}
      className={className}
      {...(rest as React.ComponentProps<typeof Badge>)}
    >
      {RenderIcon ? <RenderIcon /> : null}
      {children}
    </Badge>
  );
}

export {Tag};
