/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import * as React from 'react';
import {cva, type VariantProps} from 'class-variance-authority';
import {Slot} from 'radix-ui';

import {cn} from '../../../lib/utils';

const linkVariants = cva(
  'inline-flex items-center gap-1.5 text-primary underline-offset-4 outline-hidden transition-colors focus-visible:ring-[3px] focus-visible:ring-ring/50 hover:text-primary/80 hover:underline aria-disabled:pointer-events-none aria-disabled:opacity-50 data-[visited=true]:visited:text-purple-600',
  {
    variants: {
      size: {
        sm: 'text-xs',
        md: 'text-sm',
        lg: 'text-base',
      },
      inline: {
        true: 'underline',
        false: '',
      },
    },
    defaultVariants: {
      size: 'md',
      inline: false,
    },
  },
);

type LinkProps = Omit<React.ComponentProps<'a'>, 'children'> &
  VariantProps<typeof linkVariants> & {
    asChild?: boolean;
    disabled?: boolean;
    visited?: boolean;
    renderIcon?: React.ComponentType<{className?: string}>;
    children?: React.ReactNode;
  };

function Link({
  className,
  size,
  inline,
  asChild,
  disabled,
  visited,
  renderIcon: Icon,
  children,
  ...props
}: LinkProps) {
  const Comp = asChild ? Slot.Root : 'a';
  return (
    <Comp
      data-slot="link"
      data-visited={visited ? 'true' : undefined}
      aria-disabled={disabled || undefined}
      tabIndex={disabled ? -1 : props.tabIndex}
      className={cn(linkVariants({size, inline}), className)}
      {...props}
    >
      {asChild ? (
        children
      ) : (
        <>
          {children}
          {Icon ? <Icon className="size-4 shrink-0" /> : null}
        </>
      )}
    </Comp>
  );
}

export {Link, linkVariants};
export type {LinkProps};
