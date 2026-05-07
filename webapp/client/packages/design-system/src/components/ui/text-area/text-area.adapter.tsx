/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import * as React from 'react';

import {Textarea} from './text-area.shadcn';

import {cn, warnDroppedProps} from '../../../lib/utils';

import type {TextAreaProps as CarbonTextAreaProps} from '@carbon/react';

export type TextAreaProps = CarbonTextAreaProps;

function TextArea(props: TextAreaProps) {
  const {
    id,
    labelText,
    helperText,
    invalid,
    invalidText,
    warn,
    warnText,
    enableCounter,
    maxCount,
    counterMode,
    hideLabel,
    light,
    className,
    disabled,
    placeholder,
    value,
    defaultValue,
    onChange,
    onBlur,
    onFocus,
    rows,
    cols,
    readOnly,
    ...rest
  } = props as TextAreaProps & {
    id?: string;
    labelText?: React.ReactNode;
    helperText?: React.ReactNode;
    invalid?: boolean;
    invalidText?: React.ReactNode;
    warn?: boolean;
    warnText?: React.ReactNode;
    enableCounter?: boolean;
    maxCount?: number;
    counterMode?: string;
    hideLabel?: boolean;
    light?: boolean;
    className?: string;
    disabled?: boolean;
    placeholder?: string;
    value?: string | number | readonly string[];
    defaultValue?: string | number | readonly string[];
    onChange?: React.ChangeEventHandler<HTMLTextAreaElement>;
    onBlur?: React.FocusEventHandler<HTMLTextAreaElement>;
    onFocus?: React.FocusEventHandler<HTMLTextAreaElement>;
    rows?: number;
    cols?: number;
    readOnly?: boolean;
  };

  warnDroppedProps('TextArea', {
    helperText,
    invalid,
    invalidText,
    warn,
    warnText,
    enableCounter,
    maxCount,
    counterMode,
    hideLabel,
    light,
  });

  const textarea = (
    <Textarea
      id={id}
      className={labelText !== undefined ? undefined : className}
      disabled={disabled}
      placeholder={placeholder}
      value={value}
      defaultValue={defaultValue}
      onChange={onChange}
      onBlur={onBlur}
      onFocus={onFocus}
      rows={rows}
      cols={cols}
      readOnly={readOnly}
      {...(rest as React.ComponentProps<typeof Textarea>)}
    />
  );

  if (labelText !== undefined) {
    return (
      <label
        htmlFor={id}
        className={cn('flex flex-col gap-2 text-sm', className)}
      >
        <span>{labelText}</span>
        {textarea}
      </label>
    );
  }

  return textarea;
}

export {TextArea};
