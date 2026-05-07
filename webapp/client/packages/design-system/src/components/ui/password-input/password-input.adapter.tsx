/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import * as React from 'react';

import {PasswordInput as ShadcnPasswordInput} from './password-input.shadcn';

import {warnDroppedProps} from '../../../lib/utils';

import type {PasswordInputProps as CarbonPasswordInputProps} from '@carbon/react';

export type PasswordInputProps = CarbonPasswordInputProps;

function PasswordInput(props: PasswordInputProps) {
  const {
    className,
    disabled,
    enableCounter,
    helperText,
    hideLabel,
    hidePasswordLabel,
    id,
    inline,
    invalid,
    invalidText,
    labelText,
    light,
    maxCount,
    onChange,
    onClick,
    onTogglePasswordVisibility,
    placeholder,
    readOnly,
    showPasswordLabel,
    size,
    tooltipAlignment,
    tooltipPosition,
    type,
    value,
    defaultValue,
    warn,
    warnText,
    ...rest
  } = props as PasswordInputProps & {
    className?: string;
    disabled?: boolean;
    enableCounter?: boolean;
    helperText?: React.ReactNode;
    hideLabel?: boolean;
    hidePasswordLabel?: string;
    id: string;
    inline?: boolean;
    invalid?: boolean;
    invalidText?: React.ReactNode;
    labelText: React.ReactNode;
    light?: boolean;
    maxCount?: number;
    onChange?: (event: React.ChangeEvent<HTMLInputElement>) => void;
    onClick?: (event: React.MouseEvent<HTMLInputElement>) => void;
    onTogglePasswordVisibility?: (
      event: React.MouseEvent<HTMLButtonElement>,
    ) => void;
    placeholder?: string;
    readOnly?: boolean;
    showPasswordLabel?: string;
    size?: 'sm' | 'md' | 'lg';
    tooltipAlignment?: 'start' | 'center' | 'end';
    tooltipPosition?: 'top' | 'right' | 'bottom' | 'left';
    type?: 'password' | 'text';
    value?: string | number;
    defaultValue?: string | number;
    warn?: boolean;
    warnText?: React.ReactNode;
  };

  warnDroppedProps('PasswordInput', {
    enableCounter,
    inline,
    light,
    maxCount,
    readOnly,
    tooltipAlignment,
    tooltipPosition,
    warn,
    warnText,
  });

  return (
    <div className={className}>
      {labelText && !hideLabel && (
        <label
          htmlFor={id}
          className="mb-1 block text-sm font-medium text-foreground"
        >
          {labelText}
        </label>
      )}
      <ShadcnPasswordInput
        id={id}
        disabled={disabled}
        size={size}
        placeholder={placeholder}
        value={value}
        defaultValue={defaultValue}
        onChange={onChange}
        onClick={onClick}
        onTogglePasswordVisibility={onTogglePasswordVisibility}
        hidePasswordLabel={hidePasswordLabel}
        showPasswordLabel={showPasswordLabel}
        aria-invalid={invalid || undefined}
        {...(rest as Omit<React.InputHTMLAttributes<HTMLInputElement>, 'size'>)}
      />
      {invalid && invalidText && (
        <p className="mt-1 text-sm text-destructive">{invalidText}</p>
      )}
      {!invalid && warn && warnText && (
        <p className="mt-1 text-sm text-amber-600 dark:text-amber-500">
          {warnText}
        </p>
      )}
      {!invalid && !warn && helperText && (
        <p className="mt-1 text-sm text-muted-foreground">{helperText}</p>
      )}
    </div>
  );
}

export {PasswordInput};
