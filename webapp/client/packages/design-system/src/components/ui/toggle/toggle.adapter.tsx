/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * Carbon's `Toggle` is a binary switch with built-in label slots
 * (labelA/labelB/labelText) — labelling is part of the component. shadcn's
 * `Switch` is headless: the adapter composes a `<label>` alongside it to
 * replicate `labelText`/`hideLabel`/`labelA`/`labelB`/`onClick`. Carbon
 * `toggled`/`onToggle` map to shadcn `checked`/`onCheckedChange`. NOT
 * shadcn `Toggle` — that's a pressable
 * button, semantically closer to Carbon's `IconButton isSelected`.
 */

import * as React from 'react';

import {Switch} from '../switch/switch.shadcn';

import {cn, warnDroppedProps} from '../../../lib/utils';

import type {ToggleProps as CarbonToggleProps} from '@carbon/react';

export type ToggleProps = CarbonToggleProps;

type CarbonToggleSize = 'sm' | 'md';
type SwitchSize = NonNullable<React.ComponentProps<typeof Switch>['size']>;

const SIZE_MAP: Record<CarbonToggleSize, SwitchSize> = {
  sm: 'sm',
  md: 'default',
};

function Toggle(props: ToggleProps) {
  const {
    'aria-labelledby': ariaLabelledby,
    className,
    defaultToggled,
    disabled,
    hideLabel,
    id,
    labelA,
    labelB,
    labelText,
    onClick,
    onToggle,
    readOnly,
    size,
    toggled,
    ...rest
  } = props;

  warnDroppedProps('Toggle', {readOnly});

  const switchSize: SwitchSize = size ? SIZE_MAP[size] : 'default';

  // labelText takes priority; fall back to state-dependent labelA/labelB.
  const labelContent = labelText ?? (toggled ? labelB : labelA);

  const switchEl = (
    <Switch
      id={id}
      aria-labelledby={ariaLabelledby}
      disabled={disabled}
      size={switchSize}
      checked={toggled}
      defaultChecked={defaultToggled}
      onClick={onClick as React.MouseEventHandler<HTMLButtonElement>}
      onCheckedChange={(checked) => onToggle?.(checked)}
      {...(rest as React.ComponentProps<typeof Switch>)}
    />
  );

  if (!labelContent) {
    return React.cloneElement(switchEl, {className});
  }

  return (
    <div className={cn('flex items-center gap-2', className)}>
      {switchEl}
      <label
        htmlFor={id}
        className={cn(
          'text-sm font-medium leading-none',
          hideLabel && 'sr-only',
          disabled && 'cursor-not-allowed opacity-70',
        )}
      >
        {labelContent}
      </label>
    </div>
  );
}

export {Toggle};
