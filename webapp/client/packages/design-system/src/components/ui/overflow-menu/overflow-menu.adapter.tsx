/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * Carbon's `OverflowMenu` is the icon-trigger sibling of `MenuButton` — same
 * action menu, but the trigger is a `⋮` icon button. The adapter composes a
 * ghost icon `Button` + `MoreVertical` icon inside a `DropdownMenu`.
 * `OverflowMenuItem` maps to `DropdownMenuItem`; `hasDivider` renders a
 * `DropdownMenuSeparator` before the item, `isDelete` flips variant to
 * destructive.
 */

import * as React from 'react';
import {MoreVerticalIcon} from 'lucide-react';

import {Button} from '../button/button.shadcn';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '../dropdown-menu/dropdown-menu.shadcn';

import {warnDroppedProps} from '../../../lib/utils';

import type {OverflowMenuProps as CarbonOverflowMenuProps} from '@carbon/react';

type CarbonOverflowMenuItemProps = {
  children?: React.ReactNode;
  className?: string;
  closeMenu?: () => void;
  dangerDescription?: string;
  disabled?: boolean;
  hasDivider?: boolean;
  href?: string;
  isDelete?: boolean;
  itemText?: React.ReactNode;
  onClick?: React.MouseEventHandler<HTMLDivElement>;
  requireTitle?: boolean;
  title?: string;
  wrapperClassName?: string;
};

export type OverflowMenuProps = CarbonOverflowMenuProps;
export type OverflowMenuItemProps = CarbonOverflowMenuItemProps;

type CarbonOverflowMenuSize = 'xs' | 'sm' | 'md' | 'lg';
type ButtonSize = NonNullable<React.ComponentProps<typeof Button>['size']>;

const SIZE_TO_BUTTON_SIZE: Record<CarbonOverflowMenuSize, ButtonSize> = {
  xs: 'icon-xs',
  sm: 'icon-sm',
  md: 'icon',
  lg: 'icon-lg',
};

function OverflowMenu(props: OverflowMenuProps) {
  const {
    'aria-label': ariaLabel,
    ariaLabel: ariaLabelDeprecated,
    children,
    className,
    direction,
    flipped,
    focusTrap,
    iconClass,
    iconDescription,
    id,
    innerRef,
    light,
    menuOffset,
    menuOffsetFlip,
    menuOptionsClass,
    onClick,
    onClose,
    onOpen,
    open,
    renderIcon: RenderIcon,
    selectorPrimaryFocus,
    size,
  } = props as OverflowMenuProps & {size?: CarbonOverflowMenuSize};

  warnDroppedProps('OverflowMenu', {
    direction,
    flipped,
    focusTrap,
    iconClass,
    innerRef,
    light,
    menuOffset,
    menuOffsetFlip,
    menuOptionsClass,
    selectorPrimaryFocus,
  });

  const buttonSize: ButtonSize = size ? SIZE_TO_BUTTON_SIZE[size] : 'icon';
  const accessibleLabel = ariaLabel ?? ariaLabelDeprecated ?? 'More';

  const handleOpenChange = (next: boolean) => {
    if (next) onOpen?.();
    else onClose?.();
  };

  return (
    <DropdownMenu open={open} onOpenChange={handleOpenChange}>
      <DropdownMenuTrigger asChild>
        <Button
          id={id}
          variant="ghost"
          size={buttonSize}
          aria-label={accessibleLabel}
          title={iconDescription}
          className={className}
          onClick={onClick}
        >
          {RenderIcon ? <RenderIcon /> : <MoreVerticalIcon />}
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent>{children}</DropdownMenuContent>
    </DropdownMenu>
  );
}

function OverflowMenuItem(props: OverflowMenuItemProps) {
  const {
    children,
    className,
    closeMenu,
    dangerDescription,
    disabled,
    hasDivider,
    href,
    isDelete,
    itemText,
    onClick,
    requireTitle,
    title,
    wrapperClassName,
  } = props;

  warnDroppedProps('OverflowMenuItem', {
    closeMenu,
    dangerDescription,
    requireTitle,
    wrapperClassName,
  });

  const variant: 'default' | 'destructive' = isDelete ? 'destructive' : 'default';

  const item = (
    <DropdownMenuItem
      className={className}
      disabled={disabled}
      variant={variant}
      onClick={onClick}
      title={title}
    >
      {href ? (
        <a href={href} className="block w-full">
          {itemText ?? children}
        </a>
      ) : (
        <>
          {itemText}
          {children}
        </>
      )}
    </DropdownMenuItem>
  );

  if (hasDivider) {
    return (
      <>
        <DropdownMenuSeparator />
        {item}
      </>
    );
  }

  return item;
}

export {OverflowMenu, OverflowMenuItem};
