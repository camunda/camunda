/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * Carbon's `DatePicker` is a monolithic field with a flatpickr calendar.
 * shadcn does not ship a `DatePicker` component — it ships only the
 * `Calendar` primitive (built on `react-day-picker`). The vanilla
 * "date-picker" pattern is a composition of `Popover` + `Calendar` + a
 * trigger `Button` showing the formatted date. The canonical primitives
 * live at `calendar/calendar.shadcn.tsx` and `popover/popover.shadcn.tsx`
 * and are re-exported here for migration ergonomics.
 */

export {Calendar, CalendarDayButton} from '../calendar/calendar.shadcn';
export {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from '../popover/popover.shadcn';
