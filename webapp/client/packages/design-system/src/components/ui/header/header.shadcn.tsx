/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import * as React from 'react';
import {Slot} from 'radix-ui';

import {cn} from '../../../lib/utils';

type HeaderProps = React.ComponentPropsWithoutRef<'header'>;

function Header({className, ...props}: HeaderProps) {
  return (
    <header
      data-slot="header"
      className={cn(
        'sticky top-0 z-30 flex h-12 w-full items-stretch border-b bg-card text-card-foreground',
        className,
      )}
      {...props}
    />
  );
}

type HeaderNameProps = React.ComponentPropsWithoutRef<'a'> & {
  asChild?: boolean;
  prefix?: React.ReactNode;
};

function HeaderName({
  asChild,
  className,
  children,
  prefix,
  ...props
}: HeaderNameProps) {
  const Comp = asChild ? Slot.Root : 'a';
  return (
    <Comp
      data-slot="header-name"
      className={cn(
        'flex shrink-0 items-center gap-2 px-4 text-sm font-semibold tracking-tight outline-hidden hover:bg-accent focus-visible:bg-accent',
        className,
      )}
      {...props}
    >
      {prefix && (
        <span className="text-muted-foreground font-normal">{prefix}</span>
      )}
      {children}
    </Comp>
  );
}

type HeaderNavigationProps = React.ComponentPropsWithoutRef<'nav'>;

function HeaderNavigation({
  className,
  children,
  ...props
}: HeaderNavigationProps) {
  return (
    <nav
      data-slot="header-navigation"
      className={cn('flex items-stretch', className)}
      {...props}
    >
      <ul className="flex items-stretch">{children}</ul>
    </nav>
  );
}

type HeaderMenuItemProps = React.ComponentPropsWithoutRef<'a'> & {
  asChild?: boolean;
  isActive?: boolean;
};

function HeaderMenuItem({
  asChild,
  className,
  isActive,
  ...props
}: HeaderMenuItemProps) {
  const Comp = asChild ? Slot.Root : 'a';
  return (
    <li className="flex items-stretch">
      <Comp
        data-slot="header-menu-item"
        data-active={isActive || undefined}
        aria-current={isActive ? 'page' : undefined}
        className={cn(
          'flex items-center px-4 text-sm text-muted-foreground outline-hidden transition-colors hover:bg-accent hover:text-foreground focus-visible:bg-accent data-[active=true]:bg-accent data-[active=true]:text-foreground',
          className,
        )}
        {...props}
      />
    </li>
  );
}

type HeaderGlobalBarProps = React.ComponentPropsWithoutRef<'div'>;

function HeaderGlobalBar({className, ...props}: HeaderGlobalBarProps) {
  return (
    <div
      data-slot="header-global-bar"
      className={cn('ml-auto flex items-stretch', className)}
      {...props}
    />
  );
}

type HeaderGlobalActionProps = React.ComponentPropsWithoutRef<'button'> & {
  isActive?: boolean;
  tooltipAlignment?: 'start' | 'center' | 'end';
};

function HeaderGlobalAction({
  className,
  isActive,
  tooltipAlignment: _tooltipAlignment,
  ...props
}: HeaderGlobalActionProps) {
  return (
    <button
      type="button"
      data-slot="header-global-action"
      data-active={isActive || undefined}
      className={cn(
        'flex aspect-square items-center justify-center text-muted-foreground outline-hidden transition-colors hover:bg-accent hover:text-foreground focus-visible:bg-accent data-[active=true]:bg-accent data-[active=true]:text-foreground',
        '[&_svg]:size-5',
        className,
      )}
      {...props}
    />
  );
}

export {
  Header,
  HeaderName,
  HeaderNavigation,
  HeaderMenuItem,
  HeaderGlobalBar,
  HeaderGlobalAction,
};
export type {
  HeaderProps,
  HeaderNameProps,
  HeaderNavigationProps,
  HeaderMenuItemProps,
  HeaderGlobalBarProps,
  HeaderGlobalActionProps,
};
