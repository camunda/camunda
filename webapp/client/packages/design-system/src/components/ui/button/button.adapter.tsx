/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import * as React from 'react';

import {Button as ShadcnButton} from './button.shadcn';

import {warnDroppedProps} from '../../../lib/utils';

import type {ButtonProps as CarbonButtonProps} from '@carbon/react';

export type ButtonProps<T extends React.ElementType = 'button'> =
  CarbonButtonProps<T>;

type CarbonKind =
  | 'primary'
  | 'secondary'
  | 'tertiary'
  | 'ghost'
  | 'danger'
  | 'danger--tertiary'
  | 'danger--ghost';

type ShadcnVariant = NonNullable<
  React.ComponentProps<typeof ShadcnButton>['variant']
>;
type ShadcnSize = NonNullable<React.ComponentProps<typeof ShadcnButton>['size']>;

const KIND_TO_VARIANT: Record<CarbonKind, ShadcnVariant> = {
  primary: 'default',
  secondary: 'secondary',
  tertiary: 'outline',
  ghost: 'ghost',
  danger: 'destructive',
  'danger--tertiary': 'destructive',
  'danger--ghost': 'destructive',
};

type CarbonSize = 'sm' | 'md' | 'lg' | 'xl' | '2xl';

const SIZE_TO_SIZE: Record<CarbonSize, ShadcnSize> = {
  sm: 'sm',
  md: 'default',
  lg: 'default',
  xl: 'lg',
  '2xl': 'lg',
};

function Button(props: ButtonProps<'button'>) {
  const {
    kind,
    size,
    renderIcon: RenderIcon,
    iconDescription,
    hasIconOnly,
    isExpressive,
    isSelected,
    tooltipAlignment,
    tooltipPosition,
    as,
    children,
    className,
    ...rest
  } = props as ButtonProps<'button'> & {
    kind?: CarbonKind;
    size?: CarbonSize;
    renderIcon?: React.ElementType;
    iconDescription?: string;
    hasIconOnly?: boolean;
    isExpressive?: boolean;
    isSelected?: boolean;
    tooltipAlignment?: string;
    tooltipPosition?: string;
    as?: React.ElementType;
    children?: React.ReactNode;
    className?: string;
  };

  warnDroppedProps('Button', {
    iconDescription,
    hasIconOnly,
    isExpressive,
    isSelected,
    tooltipAlignment,
    tooltipPosition,
    as,
  });

  const variant: ShadcnVariant = kind ? KIND_TO_VARIANT[kind] : 'default';
  const mappedSize: ShadcnSize = size ? SIZE_TO_SIZE[size] : 'default';

  return (
    <ShadcnButton
      variant={variant}
      size={mappedSize}
      className={className}
      {...(rest as React.ComponentProps<typeof ShadcnButton>)}
    >
      {RenderIcon ? <RenderIcon /> : null}
      {children}
    </ShadcnButton>
  );
}

export {Button};
