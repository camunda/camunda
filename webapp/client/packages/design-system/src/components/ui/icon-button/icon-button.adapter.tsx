/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * Carbon's `IconButton` is a square button with a built-in tooltip driven by
 * the `label` prop. The adapter composes a shadcn `Button` (size="icon", with
 * `aria-label`) inside a `Tooltip`. `align` maps to TooltipContent `side`,
 * `enterDelayMs`/`leaveDelayMs` propagate via TooltipProvider's
 * `delayDuration`. `isSelected` has no equivalent in `Button` (consumers
 * needing a toggleable state should switch to a Toggle primitive).
 */

import * as React from 'react';

import {Button} from '../button/button.shadcn';
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from '../tooltip/tooltip.shadcn';

import {warnDroppedProps} from '../../../lib/utils';

import type {IconButtonProps as CarbonIconButtonProps} from '@carbon/react';

export type IconButtonProps = CarbonIconButtonProps;

type CarbonIconButtonKind = 'primary' | 'secondary' | 'ghost' | 'tertiary';
type CarbonIconButtonSize = 'xs' | 'sm' | 'md' | 'lg';
type CarbonIconButtonAlign =
  | 'top'
  | 'top-left'
  | 'top-right'
  | 'top-start'
  | 'top-end'
  | 'bottom'
  | 'bottom-left'
  | 'bottom-right'
  | 'bottom-start'
  | 'bottom-end'
  | 'left'
  | 'left-top'
  | 'left-bottom'
  | 'left-end'
  | 'left-start'
  | 'right'
  | 'right-top'
  | 'right-bottom'
  | 'right-end'
  | 'right-start';

type ButtonVariant = NonNullable<React.ComponentProps<typeof Button>['variant']>;
type ButtonSize = NonNullable<React.ComponentProps<typeof Button>['size']>;
type TooltipSide = NonNullable<
  React.ComponentProps<typeof TooltipContent>['side']
>;

const KIND_TO_VARIANT: Record<CarbonIconButtonKind, ButtonVariant> = {
  primary: 'default',
  secondary: 'secondary',
  tertiary: 'outline',
  ghost: 'ghost',
};

const SIZE_TO_BUTTON_SIZE: Record<CarbonIconButtonSize, ButtonSize> = {
  xs: 'icon-xs',
  sm: 'icon-sm',
  md: 'icon',
  lg: 'icon-lg',
};

function alignToSide(align: CarbonIconButtonAlign | undefined): TooltipSide {
  if (!align) return 'top';
  if (align.startsWith('top')) return 'top';
  if (align.startsWith('bottom')) return 'bottom';
  if (align.startsWith('left')) return 'left';
  if (align.startsWith('right')) return 'right';
  return 'top';
}

function IconButton(props: IconButtonProps) {
  const {
    align,
    autoAlign,
    badgeCount,
    children,
    className,
    closeOnActivation,
    defaultOpen,
    disabled,
    dropShadow,
    enterDelayMs,
    highContrast,
    isSelected,
    kind,
    label,
    leaveDelayMs,
    size,
    wrapperClasses,
    ...rest
  } = props as IconButtonProps & {
    kind?: CarbonIconButtonKind;
    size?: CarbonIconButtonSize;
    align?: CarbonIconButtonAlign;
  };

  warnDroppedProps('IconButton', {
    autoAlign,
    badgeCount,
    closeOnActivation,
    defaultOpen,
    dropShadow,
    highContrast,
    isSelected,
  });

  const variant: ButtonVariant = kind ? KIND_TO_VARIANT[kind] : 'ghost';
  const buttonSize: ButtonSize = size ? SIZE_TO_BUTTON_SIZE[size] : 'icon';
  const side = alignToSide(align);

  const labelString = typeof label === 'string' ? label : undefined;
  const delayDuration = enterDelayMs ?? 0;

  return (
    <TooltipProvider
      delayDuration={delayDuration}
      skipDelayDuration={leaveDelayMs}
    >
      <Tooltip>
        <TooltipTrigger asChild>
          <Button
            variant={variant}
            size={buttonSize}
            aria-label={labelString}
            disabled={disabled}
            className={className}
            {...(rest as React.ComponentProps<typeof Button>)}
          >
            {children}
          </Button>
        </TooltipTrigger>
        <TooltipContent side={side} className={wrapperClasses}>
          {label}
        </TooltipContent>
      </Tooltip>
    </TooltipProvider>
  );
}

export {IconButton};
