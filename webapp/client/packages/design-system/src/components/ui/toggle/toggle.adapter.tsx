/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * Carbon's `Toggle` is a binary switch with built-in label slots
 * (labelA/labelB/labelText) — labelling is part of the component. shadcn's
 * `Switch` is headless: labels are app composition (e.g. via a sibling
 * `<label>`). Carbon `toggled`/`onToggle` map to shadcn
 * `checked`/`onCheckedChange`. NOT shadcn `Toggle` — that's a pressable
 * button, semantically closer to Carbon's `IconButton isSelected`.
 */

import * as React from 'react';

import {Switch} from '../switch/switch.shadcn';

import {warnDroppedProps} from '../../../lib/utils';

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

  warnDroppedProps('Toggle', {
    hideLabel,
    labelA,
    labelB,
    labelText,
    onClick,
    readOnly,
  });

  const switchSize: SwitchSize = size ? SIZE_MAP[size] : 'default';

  return (
    <Switch
      id={id}
      aria-labelledby={ariaLabelledby}
      className={className}
      disabled={disabled}
      size={switchSize}
      checked={toggled}
      defaultChecked={defaultToggled}
      onCheckedChange={(checked) => onToggle?.(checked)}
      {...(rest as React.ComponentProps<typeof Switch>)}
    />
  );
}

export {Toggle};
