/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import * as React from 'react';

import {
  TableSelectAll as ShadcnTableSelectAll,
  TableSelectRow as ShadcnTableSelectRow,
} from './table-select.shadcn';

import {warnDroppedProps} from '../../../lib/utils';

import type {
  TableSelectAllProps as CarbonTableSelectAllProps,
  TableSelectRowProps as CarbonTableSelectRowProps,
} from '@carbon/react';

export type TableSelectAllProps = CarbonTableSelectAllProps;
export type TableSelectRowProps = CarbonTableSelectRowProps;

function TableSelectAll(props: TableSelectAllProps) {
  const {
    'aria-label': ariaLabel,
    ariaLabel: deprecatedAriaLabel,
    checked,
    id,
    indeterminate,
    name,
    onSelect,
    disabled,
    className,
  } = props as TableSelectAllProps & {
    'aria-label'?: string;
    ariaLabel?: string;
    checked?: boolean;
    id: string;
    indeterminate?: boolean;
    name: string;
    onSelect: React.MouseEventHandler<HTMLInputElement>;
    disabled?: boolean;
    className?: string;
  };

  warnDroppedProps('TableSelectAll', {ariaLabel: deprecatedAriaLabel});

  return (
    <ShadcnTableSelectAll
      id={id}
      name={name}
      checked={checked}
      indeterminate={indeterminate}
      disabled={disabled}
      className={className}
      onSelect={onSelect}
      aria-label={ariaLabel ?? deprecatedAriaLabel}
    />
  );
}

function TableSelectRow(props: TableSelectRowProps) {
  const {
    'aria-label': ariaLabel,
    ariaLabel: deprecatedAriaLabel,
    checked,
    id,
    name,
    onSelect,
    onChange,
    disabled,
    radio,
    className,
  } = props as TableSelectRowProps & {
    'aria-label'?: string;
    ariaLabel?: string;
    checked?: boolean;
    id: string;
    name: string;
    onSelect: React.MouseEventHandler<HTMLInputElement>;
    onChange?: (
      value: boolean,
      name: string,
      event: React.ChangeEvent<HTMLInputElement>,
    ) => void;
    disabled?: boolean;
    radio?: boolean;
    className?: string;
  };

  warnDroppedProps('TableSelectRow', {ariaLabel: deprecatedAriaLabel});

  return (
    <ShadcnTableSelectRow
      id={id}
      name={name}
      checked={checked}
      disabled={disabled}
      radio={radio}
      className={className}
      onSelect={onSelect}
      onChange={onChange}
      aria-label={ariaLabel ?? deprecatedAriaLabel}
    />
  );
}

export {TableSelectAll, TableSelectRow};
