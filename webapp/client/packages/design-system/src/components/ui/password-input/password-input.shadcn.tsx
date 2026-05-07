/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import * as React from 'react';
import {EyeIcon, EyeOffIcon} from 'lucide-react';

import {cn} from '../../../lib/utils';
import {
  InputGroup,
  InputGroupAddon,
  InputGroupButton,
  InputGroupInput,
} from '../input-group/input-group.shadcn';

type PasswordInputProps = Omit<
  React.InputHTMLAttributes<HTMLInputElement>,
  'size' | 'type'
> & {
  size?: 'sm' | 'md' | 'lg';
  hidePasswordLabel?: string;
  showPasswordLabel?: string;
  onTogglePasswordVisibility?: (
    event: React.MouseEvent<HTMLButtonElement>,
  ) => void;
};

const HEIGHT_BY_SIZE: Record<NonNullable<PasswordInputProps['size']>, string> = {
  sm: 'h-8',
  md: 'h-9',
  lg: 'h-10',
};

function PasswordInput({
  className,
  size = 'md',
  disabled,
  hidePasswordLabel = 'Hide password',
  showPasswordLabel = 'Show password',
  onTogglePasswordVisibility,
  ...rest
}: PasswordInputProps) {
  const [visible, setVisible] = React.useState(false);
  const Icon = visible ? EyeOffIcon : EyeIcon;
  const label = visible ? hidePasswordLabel : showPasswordLabel;

  return (
    <InputGroup
      data-slot="password-input"
      className={cn(HEIGHT_BY_SIZE[size], 'w-full', className)}
      data-disabled={disabled || undefined}
    >
      <InputGroupInput
        type={visible ? 'text' : 'password'}
        disabled={disabled}
        className={cn(HEIGHT_BY_SIZE[size])}
        {...rest}
      />
      <InputGroupAddon align="inline-end">
        <InputGroupButton
          type="button"
          size="icon-xs"
          variant="ghost"
          aria-label={label}
          title={label}
          disabled={disabled}
          onClick={(event) => {
            setVisible((v) => !v);
            onTogglePasswordVisibility?.(event);
          }}
        >
          <Icon className="pointer-events-none" aria-hidden="true" />
        </InputGroupButton>
      </InputGroupAddon>
    </InputGroup>
  );
}

export {PasswordInput};
export type {PasswordInputProps};
