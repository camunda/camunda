/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {format} from 'date-fns';
import type {Meta, StoryObj} from '@storybook/react';
import {Calendar as CalendarIcon} from 'lucide-react';
import * as React from 'react';
import type {DateRange} from 'react-day-picker';
import {Button} from '../button/button.shadcn';
import {cn} from '@/lib/utils';
import {
  DatePicker as CarbonDatePicker,
  DatePickerInput as CarbonDatePickerInput,
} from './date-picker.carbon';
import {
  Calendar,
  Popover,
  PopoverContent,
  PopoverTrigger,
} from './date-picker.shadcn';

const meta: Meta = {
  title: 'UI/DatePicker',
};
export default meta;

type Story = StoryObj;

const SingleDatePicker = () => {
  const [date, setDate] = React.useState<Date>();
  return (
    <Popover>
      <PopoverTrigger asChild>
        <Button
          variant="outline"
          className={cn(
            'w-[240px] justify-start text-left font-normal',
            !date && 'text-muted-foreground',
          )}
        >
          <CalendarIcon className="mr-2 h-4 w-4" />
          {date ? format(date, 'PPP') : <span>Pick a date</span>}
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-auto p-0">
        <Calendar mode="single" selected={date} onSelect={setDate} />
      </PopoverContent>
    </Popover>
  );
};

export const SingleDate: Story = {
  render: () => (
    <div className="grid grid-cols-2 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonDatePicker datePickerType="single">
          <CarbonDatePickerInput
            id="dp-carbon-single"
            labelText="Pick a date"
            placeholder="mm/dd/yyyy"
          />
        </CarbonDatePicker>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">
          shadcn (compose Popover + Calendar)
        </div>
        <div className="flex flex-col gap-1.5">
          <label className="text-sm font-medium">Pick a date</label>
          <SingleDatePicker />
        </div>
      </div>
    </div>
  ),
};

const RangeDatePicker = () => {
  const [range, setRange] = React.useState<DateRange | undefined>();
  const label =
    range?.from && range?.to
      ? `${format(range.from, 'LLL d, y')} – ${format(range.to, 'LLL d, y')}`
      : range?.from
        ? format(range.from, 'LLL d, y')
        : 'Pick a range';
  return (
    <Popover>
      <PopoverTrigger asChild>
        <Button
          variant="outline"
          className={cn(
            'w-[280px] justify-start text-left font-normal',
            !range?.from && 'text-muted-foreground',
          )}
        >
          <CalendarIcon className="mr-2 h-4 w-4" />
          {label}
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-auto p-0">
        <Calendar
          mode="range"
          selected={range}
          onSelect={setRange}
          numberOfMonths={2}
        />
      </PopoverContent>
    </Popover>
  );
};

export const Range: Story = {
  render: () => (
    <div className="grid grid-cols-2 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonDatePicker datePickerType="range">
          <CarbonDatePickerInput
            id="dp-carbon-range-from"
            labelText="Start date"
            placeholder="mm/dd/yyyy"
          />
          <CarbonDatePickerInput
            id="dp-carbon-range-to"
            labelText="End date"
            placeholder="mm/dd/yyyy"
          />
        </CarbonDatePicker>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <div className="flex flex-col gap-1.5">
          <label className="text-sm font-medium">Date range</label>
          <RangeDatePicker />
        </div>
      </div>
    </div>
  ),
};

export const InlineCalendar: Story = {
  name: 'Inline calendar',
  render: () => (
    <div className="grid grid-cols-2 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">
          Carbon (no inline-calendar variant)
        </div>
        <CarbonDatePicker datePickerType="single">
          <CarbonDatePickerInput
            id="dp-carbon-inline"
            labelText="Date"
            placeholder="mm/dd/yyyy"
          />
        </CarbonDatePicker>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">
          shadcn (Calendar standalone, no Popover)
        </div>
        <Calendar mode="single" />
      </div>
    </div>
  ),
};
