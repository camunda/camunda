/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import * as React from 'react';

import {
  Popover as ShadcnPopover,
  PopoverContent as ShadcnPopoverContent,
  PopoverTrigger,
} from './popover.shadcn';

import {warnDroppedProps} from '../../../lib/utils';

import type {
  PopoverContentProps as CarbonPopoverContentProps,
  PopoverProps as CarbonPopoverProps,
} from '@carbon/react';

export type PopoverProps<E extends React.ElementType = 'span'> =
  CarbonPopoverProps<E>;
export type PopoverContentProps = CarbonPopoverContentProps;

type CarbonAlign =
  | 'top'
  | 'top-left'
  | 'top-start'
  | 'top-right'
  | 'top-end'
  | 'bottom'
  | 'bottom-left'
  | 'bottom-start'
  | 'bottom-right'
  | 'bottom-end'
  | 'left'
  | 'left-bottom'
  | 'left-end'
  | 'left-top'
  | 'left-start'
  | 'right'
  | 'right-bottom'
  | 'right-end'
  | 'right-top'
  | 'right-start';

type ShadcnSide = 'top' | 'right' | 'bottom' | 'left';
type ShadcnAlign = 'start' | 'center' | 'end';

const ALIGN_TO_SIDE_ALIGN: Record<CarbonAlign, {side: ShadcnSide; align: ShadcnAlign}> = {
  top: {side: 'top', align: 'center'},
  'top-left': {side: 'top', align: 'start'},
  'top-start': {side: 'top', align: 'start'},
  'top-right': {side: 'top', align: 'end'},
  'top-end': {side: 'top', align: 'end'},
  bottom: {side: 'bottom', align: 'center'},
  'bottom-left': {side: 'bottom', align: 'start'},
  'bottom-start': {side: 'bottom', align: 'start'},
  'bottom-right': {side: 'bottom', align: 'end'},
  'bottom-end': {side: 'bottom', align: 'end'},
  left: {side: 'left', align: 'center'},
  'left-bottom': {side: 'left', align: 'end'},
  'left-end': {side: 'left', align: 'end'},
  'left-top': {side: 'left', align: 'start'},
  'left-start': {side: 'left', align: 'start'},
  right: {side: 'right', align: 'center'},
  'right-bottom': {side: 'right', align: 'end'},
  'right-end': {side: 'right', align: 'end'},
  'right-top': {side: 'right', align: 'start'},
  'right-start': {side: 'right', align: 'start'},
};

const PopoverAlignContext = React.createContext<
  {side: ShadcnSide; align: ShadcnAlign} | null
>(null);

function Popover(props: PopoverProps<'span'>) {
  const {
    open,
    onRequestClose,
    align,
    autoAlign,
    autoAlignBoundary,
    caret,
    dropShadow,
    highContrast,
    isTabTip,
    children,
    className,
    as,
    ...rest
  } = props as PopoverProps<'span'> & {
    open?: boolean;
    onRequestClose?: () => void;
    align?: CarbonAlign;
    autoAlign?: boolean;
    autoAlignBoundary?: unknown;
    caret?: boolean;
    dropShadow?: boolean;
    highContrast?: boolean;
    isTabTip?: boolean;
    children?: React.ReactNode;
    className?: string;
    as?: React.ElementType;
  };

  warnDroppedProps('Popover', {
    autoAlign,
    autoAlignBoundary,
    caret,
    dropShadow,
    highContrast,
    isTabTip,
    as,
    className,
  });

  const onOpenChange = (next: boolean) => {
    if (!next) onRequestClose?.();
  };

  const sideAlign = align ? ALIGN_TO_SIDE_ALIGN[align] : null;

  return (
    <PopoverAlignContext.Provider value={sideAlign}>
      <ShadcnPopover
        open={open}
        onOpenChange={onOpenChange}
        {...(rest as React.ComponentProps<typeof ShadcnPopover>)}
      >
        <PopoverTrigger asChild>
          <span data-slot="popover-anchor" />
        </PopoverTrigger>
        {children}
      </ShadcnPopover>
    </PopoverAlignContext.Provider>
  );
}

function PopoverContent(props: PopoverContentProps) {
  const {children, className, ...rest} = props as PopoverContentProps & {
    children?: React.ReactNode;
    className?: string;
  };

  const sideAlign = React.useContext(PopoverAlignContext);

  return (
    <ShadcnPopoverContent
      className={className}
      side={sideAlign?.side}
      align={sideAlign?.align ?? 'center'}
      {...(rest as React.ComponentProps<typeof ShadcnPopoverContent>)}
    >
      {children}
    </ShadcnPopoverContent>
  );
}

export {Popover, PopoverContent};
