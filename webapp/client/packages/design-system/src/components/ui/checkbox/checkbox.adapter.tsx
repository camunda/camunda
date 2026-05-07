/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import * as React from 'react';

import {Checkbox as ShadcnCheckbox} from './checkbox.shadcn';

import {cn, warnDroppedProps} from '../../../lib/utils';

import type {CheckboxProps as CarbonCheckboxProps} from '@carbon/react';

export type CheckboxProps = CarbonCheckboxProps;

type CarbonChange = (
  event: React.ChangeEvent<HTMLInputElement>,
  data: {checked: boolean; id?: string},
) => void;

function Checkbox(props: CheckboxProps) {
  const {
    id,
    labelText,
    checked,
    defaultChecked,
    indeterminate,
    onChange,
    disabled,
    className,
    helperText,
    invalid,
    invalidText,
    warn,
    warnText,
    readOnly,
    hideLabel,
    title,
    ...rest
  } = props as CarbonCheckboxProps & {
    id?: string;
    labelText?: React.ReactNode;
    checked?: boolean;
    defaultChecked?: boolean;
    indeterminate?: boolean;
    onChange?: CarbonChange;
    disabled?: boolean;
    className?: string;
    helperText?: React.ReactNode;
    invalid?: boolean;
    invalidText?: React.ReactNode;
    warn?: boolean;
    warnText?: React.ReactNode;
    readOnly?: boolean;
    hideLabel?: boolean;
    title?: string;
  };

  warnDroppedProps('Checkbox', {
    helperText,
    invalid,
    invalidText,
    warn,
    warnText,
    readOnly,
    hideLabel,
  });

  const handleCheckedChange = onChange
    ? (next: boolean | 'indeterminate') => {
        const isChecked = next === true;
        const synthetic = {
          target: {checked: isChecked, id},
        } as unknown as React.ChangeEvent<HTMLInputElement>;
        onChange(synthetic, {checked: isChecked, id});
      }
    : undefined;

  const checkedValue: boolean | 'indeterminate' | undefined = indeterminate
    ? 'indeterminate'
    : checked;

  const box = (
    <ShadcnCheckbox
      id={id}
      checked={checkedValue}
      defaultChecked={defaultChecked}
      disabled={disabled}
      onCheckedChange={handleCheckedChange}
      title={title}
      {...(rest as React.ComponentProps<typeof ShadcnCheckbox>)}
    />
  );

  if (labelText !== undefined) {
    return (
      <label className={cn('flex items-center gap-2', className)}>
        {box}
        <span>{labelText}</span>
      </label>
    );
  }

  return React.cloneElement(box, {className});
}

export {Checkbox};
