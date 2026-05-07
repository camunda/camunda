/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import * as React from 'react';

import {cn} from '../../../lib/utils';
import {Checkbox} from '../checkbox/checkbox.shadcn';

type TableSelectAllProps = {
  className?: string;
  id: string;
  name: string;
  checked?: boolean;
  indeterminate?: boolean;
  disabled?: boolean;
  onSelect: React.MouseEventHandler<HTMLInputElement>;
  'aria-label'?: string;
};

function TableSelectAll({
  className,
  id,
  name,
  checked,
  indeterminate,
  disabled,
  onSelect,
  'aria-label': ariaLabel,
}: TableSelectAllProps) {
  return (
    <th
      data-slot="table-select-all"
      className={cn('w-10 px-2 align-middle', className)}
    >
      <Checkbox
        id={id}
        name={name}
        checked={indeterminate ? 'indeterminate' : checked}
        disabled={disabled}
        aria-label={ariaLabel}
        onCheckedChange={(_) => {
          // Carbon's API exposes a MouseEvent on the input. We synthesise
          // a minimal compatible target so callers that read `event.target.checked`
          // continue to work.
          const synthetic = {
            target: {checked: !checked, id, name},
          } as unknown as React.MouseEvent<HTMLInputElement>;
          onSelect(synthetic);
        }}
      />
    </th>
  );
}

type TableSelectRowProps = {
  className?: string;
  id: string;
  name: string;
  checked?: boolean;
  disabled?: boolean;
  radio?: boolean;
  onSelect: React.MouseEventHandler<HTMLInputElement>;
  onChange?: (
    value: boolean,
    name: string,
    event: React.ChangeEvent<HTMLInputElement>,
  ) => void;
  'aria-label'?: string;
};

function TableSelectRow({
  className,
  id,
  name,
  checked,
  disabled,
  radio,
  onSelect,
  onChange,
  'aria-label': ariaLabel,
}: TableSelectRowProps) {
  if (radio) {
    return (
      <td
        data-slot="table-select-row"
        className={cn('w-10 px-2 align-middle', className)}
      >
        <input
          type="radio"
          id={id}
          name={name}
          checked={!!checked}
          disabled={disabled}
          aria-label={ariaLabel}
          onChange={(event) => {
            onChange?.(event.target.checked, name, event);
          }}
          onClick={onSelect}
          className="size-4 cursor-pointer accent-primary disabled:cursor-not-allowed disabled:opacity-50"
        />
      </td>
    );
  }
  return (
    <td
      data-slot="table-select-row"
      className={cn('w-10 px-2 align-middle', className)}
    >
      <Checkbox
        id={id}
        name={name}
        checked={checked}
        disabled={disabled}
        aria-label={ariaLabel}
        onCheckedChange={(next) => {
          const synthetic = {
            target: {checked: next === true, id, name},
          } as unknown as React.MouseEvent<HTMLInputElement>;
          onSelect(synthetic);
          if (onChange) {
            onChange(
              next === true,
              name,
              synthetic as unknown as React.ChangeEvent<HTMLInputElement>,
            );
          }
        }}
      />
    </td>
  );
}

export {TableSelectAll, TableSelectRow};
export type {TableSelectAllProps, TableSelectRowProps};
