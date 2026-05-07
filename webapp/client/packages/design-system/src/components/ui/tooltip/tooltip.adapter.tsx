/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * Carbon's `<Tooltip label="...">` wraps a single child trigger and owns its
 * own delays. The adapter expands this into shadcn's compound:
 * `<TooltipProvider><Tooltip><TooltipTrigger asChild>{child}<TooltipContent>label`.
 */

import * as React from 'react';

import {
  Tooltip as ShadcnTooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from './tooltip.shadcn';

import {warnDroppedProps} from '../../../lib/utils';

import type {TooltipProps as CarbonTooltipProps} from '@carbon/react';

export type TooltipProps<T extends React.ElementType = 'div'> =
  CarbonTooltipProps<T>;

function Tooltip(props: TooltipProps<'div'>) {
  const {
    label,
    description,
    children,
    className,
    align,
    autoAlign,
    enterDelayMs,
    leaveDelayMs,
    defaultOpen,
    dropShadow,
    caret,
    open,
    onOpenChange,
    ...rest
  } = props as TooltipProps<'div'> & {
    label?: React.ReactNode;
    description?: React.ReactNode;
    children?: React.ReactNode;
    className?: string;
    align?: string;
    autoAlign?: boolean;
    enterDelayMs?: number;
    leaveDelayMs?: number;
    defaultOpen?: boolean;
    dropShadow?: boolean;
    caret?: boolean;
    open?: boolean;
    onOpenChange?: (open: boolean) => void;
  };

  warnDroppedProps('Tooltip', {
    align,
    autoAlign,
    leaveDelayMs,
    dropShadow,
    caret,
  });

  const content = label ?? description;

  return (
    <TooltipProvider delayDuration={enterDelayMs}>
      <ShadcnTooltip
        open={open}
        defaultOpen={defaultOpen}
        onOpenChange={onOpenChange}
        {...(rest as React.ComponentProps<typeof ShadcnTooltip>)}
      >
        <TooltipTrigger asChild>{children as React.ReactElement}</TooltipTrigger>
        {content !== undefined && content !== null ? (
          <TooltipContent className={className}>{content}</TooltipContent>
        ) : null}
      </ShadcnTooltip>
    </TooltipProvider>
  );
}

export {Tooltip};
