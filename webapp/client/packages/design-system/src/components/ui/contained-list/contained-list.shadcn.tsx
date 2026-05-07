/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import * as React from 'react';

import {cn} from '../../../lib/utils';

type ContainedListSize = 'sm' | 'md' | 'lg' | 'xl';
type ContainedListKind = 'on-page' | 'disclosed';

const SIZE_CLASS: Record<ContainedListSize, string> = {
  sm: 'text-xs',
  md: 'text-sm',
  lg: 'text-base',
  xl: 'text-lg',
};

const ITEM_PADDING_CLASS: Record<ContainedListSize, string> = {
  sm: 'min-h-8 px-3 py-1.5',
  md: 'min-h-10 px-4 py-2',
  lg: 'min-h-12 px-4 py-2.5',
  xl: 'min-h-14 px-5 py-3',
};

type ContainedListContextValue = {
  size: ContainedListSize;
  isInset: boolean;
};

const ContainedListContext = React.createContext<ContainedListContextValue>({
  size: 'md',
  isInset: false,
});

type ContainedListProps = Omit<React.ComponentPropsWithoutRef<'section'>, 'children'> & {
  action?: React.ReactNode;
  isInset?: boolean;
  kind?: ContainedListKind;
  label?: React.ReactNode;
  size?: ContainedListSize;
  children?: React.ReactNode;
};

function ContainedList({
  action,
  children,
  className,
  isInset = false,
  kind = 'on-page',
  label,
  size = 'md',
  ...props
}: ContainedListProps) {
  const headingId = React.useId();
  const ariaLabelledBy =
    typeof label === 'string' || (label != null && typeof label === 'object')
      ? headingId
      : undefined;
  return (
    <ContainedListContext.Provider value={{size, isInset}}>
      <section
        data-slot="contained-list"
        data-kind={kind}
        data-size={size}
        aria-labelledby={ariaLabelledBy}
        className={cn(
          'flex flex-col',
          kind === 'disclosed' && 'rounded-md border bg-card',
          className,
        )}
        {...props}
      >
        {(label != null || action != null) && (
          <header
            className={cn(
              'flex items-center justify-between gap-2 border-b border-border',
              ITEM_PADDING_CLASS[size],
              SIZE_CLASS[size],
            )}
          >
            {label != null && (
              <h3
                id={headingId}
                className="m-0 font-semibold text-muted-foreground"
              >
                {label}
              </h3>
            )}
            {action != null && <div className="ml-auto">{action}</div>}
          </header>
        )}
        <ul role="list" className="m-0 list-none p-0">
          {children}
        </ul>
      </section>
    </ContainedListContext.Provider>
  );
}

type ContainedListItemProps = Omit<
  React.ComponentPropsWithoutRef<'li'>,
  'onClick'
> & {
  action?: React.ReactNode;
  disabled?: boolean;
  onClick?: () => void;
  renderIcon?: React.ComponentType<{className?: string}>;
  children?: React.ReactNode;
};

function ContainedListItem({
  action,
  children,
  className,
  disabled,
  onClick,
  renderIcon: Icon,
  ...props
}: ContainedListItemProps) {
  const {size, isInset} = React.useContext(ContainedListContext);
  const isInteractive = onClick != null && !disabled;

  const content = (
    <>
      {Icon && (
        <Icon className="size-4 shrink-0 text-muted-foreground" aria-hidden />
      )}
      <span className="min-w-0 flex-1 truncate">{children}</span>
      {action != null && <span className="ml-auto shrink-0">{action}</span>}
    </>
  );

  return (
    <li
      data-slot="contained-list-item"
      data-disabled={disabled || undefined}
      data-interactive={isInteractive || undefined}
      className={cn(
        'border-b border-border last:border-b-0',
        isInset && 'mx-4',
        disabled && 'pointer-events-none opacity-50',
        className,
      )}
      {...props}
    >
      {isInteractive ? (
        <button
          type="button"
          onClick={onClick}
          disabled={disabled}
          className={cn(
            'flex w-full items-center gap-2 text-left transition-colors hover:bg-accent hover:text-accent-foreground focus-visible:bg-accent focus-visible:outline-none',
            ITEM_PADDING_CLASS[size],
            SIZE_CLASS[size],
          )}
        >
          {content}
        </button>
      ) : (
        <div
          className={cn(
            'flex items-center gap-2',
            ITEM_PADDING_CLASS[size],
            SIZE_CLASS[size],
          )}
        >
          {content}
        </div>
      )}
    </li>
  );
}

export {ContainedList, ContainedListItem};
export type {
  ContainedListProps,
  ContainedListItemProps,
  ContainedListSize,
  ContainedListKind,
};
