/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * Carbon's `DatePicker` wraps a flatpickr instance via children. shadcn ships
 * only `Calendar` (react-day-picker) — the date-picker pattern is a
 * composition of `Popover` + `Calendar` + a trigger `Input`. The adapter
 * builds a pragmatic, controlled wrapper: `value` (an array of Dates per
 * Carbon) is normalised into a single Date / range, `onChange` is invoked
 * with `(dates, dateStr)`. flatpickr-only props (locale, dateFormat,
 * minDate/maxDate as flatpickr options, etc.) are dropped with warning.
 */

import * as React from 'react';

import {Calendar} from '../calendar/calendar.shadcn';
import {Popover, PopoverContent, PopoverTrigger} from '../popover/popover.shadcn';
import {Input} from '../text-input/text-input.shadcn';

import {cn, warnDroppedProps} from '../../../lib/utils';

import type {
  DatePickerInputProps as CarbonDatePickerInputProps,
  DatePickerProps as CarbonDatePickerProps,
} from '@carbon/react';

export type DatePickerProps = CarbonDatePickerProps;
export type DatePickerInputProps = CarbonDatePickerInputProps;

type DateLike = Date | string | number;

function toDate(value: DateLike | null | undefined): Date | undefined {
  if (value === null || value === undefined) return undefined;
  if (value instanceof Date) return Number.isNaN(value.getTime()) ? undefined : value;
  const d = new Date(value as string | number);
  return Number.isNaN(d.getTime()) ? undefined : d;
}

function normaliseValue(
  value: CarbonDatePickerProps['value'],
): Date[] {
  if (value === undefined || value === null) return [];
  const arr = Array.isArray(value) ? value : [value];
  const result: Date[] = [];
  for (const v of arr) {
    const d = toDate(v as DateLike);
    if (d) result.push(d);
  }
  return result;
}

function DatePicker(props: DatePickerProps) {
  const {
    allowInput,
    appendTo,
    children,
    className,
    closeOnSelect,
    dateFormat,
    datePickerType = 'single',
    disable,
    enable,
    inline,
    invalid,
    light,
    locale,
    maxDate,
    minDate,
    nextMonthAriaLabel,
    onChange,
    onClose,
    onOpen,
    parseDate,
    prevMonthAriaLabel,
    readOnly,
    short,
    value,
    warn,
  } = props;

  warnDroppedProps('DatePicker', {
    allowInput,
    appendTo,
    closeOnSelect,
    dateFormat,
    disable,
    enable,
    inline,
    invalid,
    light,
    locale,
    maxDate,
    minDate,
    nextMonthAriaLabel,
    parseDate,
    prevMonthAriaLabel,
    readOnly,
    short,
    warn,
  });

  const [open, setOpen] = React.useState(false);
  const dates = normaliseValue(value);

  const handleOpenChange = (next: boolean) => {
    setOpen(next);
    if (next) onOpen?.([], '', undefined as unknown as never);
    else onClose?.([], '', undefined as unknown as never);
  };

  const fireChange = (selected: Date[]) => {
    if (!onChange) return;
    const dateStr = selected.map((d) => d.toISOString()).join(' to ');
    (onChange as (dates: Date[], str: string, instance?: unknown) => void)(
      selected,
      dateStr,
      undefined,
    );
  };

  if (datePickerType === 'range') {
    const [from, to] = dates;
    return (
      <Popover open={open} onOpenChange={handleOpenChange}>
        <PopoverTrigger asChild>
          <div className={className}>{children as React.ReactNode}</div>
        </PopoverTrigger>
        <PopoverContent className="w-auto p-0" align="start">
          <Calendar
            mode="range"
            selected={from || to ? {from, to} : undefined}
            onSelect={(range) => {
              const next: Date[] = [];
              if (range?.from) next.push(range.from);
              if (range?.to) next.push(range.to);
              fireChange(next);
            }}
          />
        </PopoverContent>
      </Popover>
    );
  }

  if (datePickerType === 'simple') {
    return <div className={className}>{children as React.ReactNode}</div>;
  }

  const single = dates[0];
  return (
    <Popover open={open} onOpenChange={handleOpenChange}>
      <PopoverTrigger asChild>
        <div className={className}>{children as React.ReactNode}</div>
      </PopoverTrigger>
      <PopoverContent className="w-auto p-0" align="start">
        <Calendar
          mode="single"
          selected={single}
          onSelect={(d) => {
            fireChange(d ? [d] : []);
          }}
        />
      </PopoverContent>
    </Popover>
  );
}

function DatePickerInput(props: DatePickerInputProps) {
  const {
    className,
    datePickerType,
    decorator,
    disabled,
    helperText,
    hideLabel,
    id,
    invalid,
    invalidText,
    labelText,
    onChange,
    onClick,
    pattern,
    placeholder,
    readOnly,
    size,
    slug,
    type,
    warn,
    warnText,
    ...rest
  } = props;

  warnDroppedProps('DatePickerInput', {
    datePickerType,
    decorator,
    invalid,
    invalidText,
    size,
    slug,
    warn,
    warnText,
  });

  return (
    <div className={cn('flex flex-col gap-1', className)}>
      {labelText !== undefined && labelText !== null ? (
        <label
          htmlFor={id}
          className={cn('text-sm font-medium', hideLabel && 'sr-only')}
        >
          {labelText as React.ReactNode}
        </label>
      ) : null}
      <Input
        id={id}
        type={type}
        pattern={pattern}
        placeholder={placeholder}
        disabled={disabled}
        readOnly={readOnly}
        onChange={onChange as React.ChangeEventHandler<HTMLInputElement>}
        onClick={onClick as React.MouseEventHandler<HTMLInputElement>}
        aria-invalid={invalid || undefined}
        {...(rest as React.ComponentProps<typeof Input>)}
      />
      {helperText !== undefined && helperText !== null ? (
        <span className="text-xs text-muted-foreground">
          {helperText as React.ReactNode}
        </span>
      ) : null}
    </div>
  );
}

export {DatePicker, DatePickerInput};
