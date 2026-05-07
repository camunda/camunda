/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * Carbon's `MenuButton` is a labelled button that opens an action menu of
 * `MenuItem`s. The adapter composes a shadcn `Button` (with chevron) inside
 * a `DropdownMenu`. `MenuItem` becomes `DropdownMenuItem`; `kind="danger"`
 * maps to `variant="destructive"`. `shortcut` and `renderIcon` are dropped.
 */

import * as React from 'react';
import {ChevronDownIcon} from 'lucide-react';

import {Button} from '../button/button.shadcn';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '../dropdown-menu/dropdown-menu.shadcn';

import {warnDroppedProps} from '../../../lib/utils';

import type {
  MenuButtonProps as CarbonMenuButtonProps,
  MenuItemProps as CarbonMenuItemProps,
} from '@carbon/react';

export type MenuButtonProps = CarbonMenuButtonProps;
export type MenuItemProps = CarbonMenuItemProps;

type CarbonMenuButtonKind = 'primary' | 'tertiary' | 'ghost';
type CarbonMenuButtonSize = 'xs' | 'sm' | 'md' | 'lg';
type CarbonMenuAlignment =
  | 'top'
  | 'top-start'
  | 'top-end'
  | 'bottom'
  | 'bottom-start'
  | 'bottom-end';

type ButtonVariant = NonNullable<React.ComponentProps<typeof Button>['variant']>;
type ButtonSize = NonNullable<React.ComponentProps<typeof Button>['size']>;
type ContentSide = NonNullable<
  React.ComponentProps<typeof DropdownMenuContent>['side']
>;
type ContentAlign = NonNullable<
  React.ComponentProps<typeof DropdownMenuContent>['align']
>;

const KIND_TO_VARIANT: Record<CarbonMenuButtonKind, ButtonVariant> = {
  primary: 'default',
  tertiary: 'outline',
  ghost: 'ghost',
};

const SIZE_TO_BUTTON_SIZE: Record<CarbonMenuButtonSize, ButtonSize> = {
  xs: 'xs',
  sm: 'sm',
  md: 'default',
  lg: 'lg',
};

function menuAlignmentToSideAlign(alignment: CarbonMenuAlignment | undefined): {
  side: ContentSide;
  align: ContentAlign;
} {
  if (!alignment) return {side: 'bottom', align: 'start'};
  const side: ContentSide = alignment.startsWith('top') ? 'top' : 'bottom';
  if (alignment.endsWith('-start')) return {side, align: 'start'};
  if (alignment.endsWith('-end')) return {side, align: 'end'};
  return {side, align: 'center'};
}

function MenuButton(props: MenuButtonProps) {
  const {
    children,
    className,
    disabled,
    kind,
    label,
    menuAlignment,
    menuBackgroundToken,
    menuBorder,
    menuTarget,
    size,
    tabIndex,
  } = props as MenuButtonProps & {
    kind?: CarbonMenuButtonKind;
    size?: CarbonMenuButtonSize;
    menuAlignment?: CarbonMenuAlignment;
  };

  warnDroppedProps('MenuButton', {
    menuBackgroundToken,
    menuBorder,
    menuTarget,
  });

  const variant: ButtonVariant = kind ? KIND_TO_VARIANT[kind] : 'default';
  const buttonSize: ButtonSize = size
    ? SIZE_TO_BUTTON_SIZE[size]
    : 'default';
  const {side, align} = menuAlignmentToSideAlign(menuAlignment);

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button
          variant={variant}
          size={buttonSize}
          className={className}
          disabled={disabled}
          tabIndex={tabIndex}
        >
          {label}
          <ChevronDownIcon />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent side={side} align={align}>
        {children}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

function MenuItem(props: MenuItemProps) {
  const {
    children,
    className,
    dangerDescription,
    disabled,
    kind,
    label,
    onClick,
    renderIcon: RenderIcon,
    shortcut,
  } = props;

  warnDroppedProps('MenuItem', {
    dangerDescription,
    shortcut,
  });

  const variant: 'default' | 'destructive' =
    kind === 'danger' ? 'destructive' : 'default';

  return (
    <DropdownMenuItem
      className={className}
      disabled={disabled}
      variant={variant}
      onClick={
        onClick as
          | ((event: React.MouseEvent<HTMLDivElement>) => void)
          | undefined
      }
    >
      {RenderIcon ? <RenderIcon /> : null}
      {label}
      {children}
    </DropdownMenuItem>
  );
}

export {MenuButton, MenuItem};
