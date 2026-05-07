/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import * as React from 'react';

import {
  Select as ShadcnSelect,
  SelectContent,
  SelectItem as ShadcnSelectItem,
  SelectTrigger,
  SelectValue,
} from './select.shadcn';

import {warnDroppedProps} from '../../../lib/utils';

import type {
  SelectItemProps as CarbonSelectItemProps,
  SelectProps as CarbonSelectProps,
} from '@carbon/react';

export type SelectProps = CarbonSelectProps;
export type SelectItemProps = CarbonSelectItemProps;

type CarbonChange = (event: React.ChangeEvent<HTMLSelectElement>) => void;

function Select(props: SelectProps) {
  const {
    id,
    value,
    defaultValue,
    onChange,
    disabled,
    children,
    className,
    placeholder,
    labelText,
    helperText,
    invalid,
    invalidText,
    warn,
    warnText,
    hideLabel,
    inline,
    light,
    size,
    readOnly,
    noLabel,
    'aria-label': ariaLabel,
  } = props as SelectProps & {
    id?: string;
    value?: string;
    defaultValue?: string;
    onChange?: CarbonChange;
    disabled?: boolean;
    children?: React.ReactNode;
    className?: string;
    placeholder?: string;
    labelText?: React.ReactNode;
    helperText?: React.ReactNode;
    invalid?: boolean;
    invalidText?: React.ReactNode;
    warn?: boolean;
    warnText?: React.ReactNode;
    hideLabel?: boolean;
    inline?: boolean;
    light?: boolean;
    size?: 'sm' | 'md' | 'lg';
    readOnly?: boolean;
    noLabel?: boolean;
    'aria-label'?: string;
  };

  warnDroppedProps('Select', {
    helperText,
    invalid,
    invalidText,
    warn,
    warnText,
    hideLabel,
    inline,
    light,
    readOnly,
    noLabel,
  });

  const handleValueChange = (next: string) => {
    if (!onChange) return;
    const synthetic = {
      target: {value: next, id},
    } as unknown as React.ChangeEvent<HTMLSelectElement>;
    onChange(synthetic);
  };

  const triggerSize: 'sm' | 'default' = size === 'sm' ? 'sm' : 'default';

  return (
    <ShadcnSelect
      value={value}
      defaultValue={defaultValue}
      onValueChange={handleValueChange}
      disabled={disabled}
    >
      <SelectTrigger
        id={id}
        size={triggerSize}
        className={className}
        aria-label={ariaLabel ?? (typeof labelText === 'string' ? labelText : undefined)}
      >
        <SelectValue placeholder={placeholder} />
      </SelectTrigger>
      <SelectContent>{children}</SelectContent>
    </ShadcnSelect>
  );
}

function SelectItem(props: SelectItemProps) {
  const {
    value,
    text,
    disabled,
    hidden,
    className,
    ...rest
  } = props as SelectItemProps & {
    value: string | number;
    text?: React.ReactNode;
    disabled?: boolean;
    hidden?: boolean;
    className?: string;
  };

  warnDroppedProps('SelectItem', {hidden});

  return (
    <ShadcnSelectItem
      value={String(value)}
      disabled={disabled}
      className={className}
      {...(rest as Omit<React.ComponentProps<typeof ShadcnSelectItem>, 'value'>)}
    >
      {text}
    </ShadcnSelectItem>
  );
}

export {Select, SelectItem};
