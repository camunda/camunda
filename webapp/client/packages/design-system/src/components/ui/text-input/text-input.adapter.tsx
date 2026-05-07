/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * Carbon's `TextInput` is a monolithic field bundling label, helper text,
 * invalid/warn states, and an `<input>`. shadcn's `Input` is the bare
 * `<input>`; the label / helper text / states are reconstructed inline here.
 */

import * as React from 'react';

import {Input} from './text-input.shadcn';

import {cn, warnDroppedProps} from '../../../lib/utils';

import type {TextInputProps as CarbonTextInputProps} from '@carbon/react';

export type TextInputProps = CarbonTextInputProps;

function TextInput(props: TextInputProps) {
  const {
    className,
    decorator,
    defaultValue,
    disabled,
    enableCounter,
    helperText,
    hideLabel,
    id,
    inline,
    invalid,
    invalidText,
    labelText,
    light,
    maxCount,
    onChange,
    onClick,
    placeholder,
    readOnly,
    size,
    slug,
    type,
    value,
    warn,
    warnText,
    ...rest
  } = props;

  warnDroppedProps('TextInput', {
    decorator,
    enableCounter,
    inline,
    light,
    maxCount,
    size,
    slug,
    warn,
    warnText,
  });

  return (
    <div className={cn('flex flex-col gap-1', className)}>
      {labelText !== undefined ? (
        <label
          htmlFor={id}
          className={cn(
            'text-sm font-medium',
            hideLabel && 'sr-only',
          )}
        >
          {labelText}
        </label>
      ) : null}
      <Input
        id={id}
        type={type}
        value={value}
        defaultValue={defaultValue}
        disabled={disabled}
        readOnly={readOnly}
        placeholder={placeholder}
        onChange={onChange}
        onClick={onClick}
        aria-invalid={invalid || undefined}
        {...(rest as React.ComponentProps<typeof Input>)}
      />
      {invalid && invalidText !== undefined ? (
        <span className="text-xs text-destructive">{invalidText}</span>
      ) : null}
      {!invalid && helperText !== undefined ? (
        <span className="text-xs text-muted-foreground">{helperText}</span>
      ) : null}
    </div>
  );
}

export {TextInput};
