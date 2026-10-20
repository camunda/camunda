/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Form} from 'react-final-form';
import {
	Button,
	Calendar,
	Dialog,
	DialogBody,
	DialogContent,
	DialogFooter,
	DialogHeader,
	DialogTitle,
	type DateRange,
} from '@camunda/design-system';
import {logger} from '#/operate/shared/utils/logger';
import {formatDate} from '#/operate/shared/DateRangeField/formatDate';
import {DateInput} from './DateInput';
import {TimeInput} from './TimeInput';
import {parseCompleteDate} from './dateValidation';

const defaultTime = {
	from: '00:00:00',
	to: '23:59:59',
};

type Props = {
	title: string;
	onCancel: () => void;
	onApply: ({fromDateTime, toDateTime}: {fromDateTime: Date; toDateTime: Date}) => void;
	defaultValues: {
		fromDate: string;
		fromTime: string;
		toDate: string;
		toTime: string;
	};
	isModalOpen: boolean;
};

const DateRangeModal: React.FC<Props> = ({defaultValues, onApply, onCancel, title, isModalOpen}) => {
	const handleApply = ({
		fromDate,
		fromTime,
		toDate,
		toTime,
	}: {
		fromDate?: string;
		fromTime?: string;
		toDate?: string;
		toTime?: string;
	}) => {
		if (fromDate !== undefined && fromTime !== undefined && toDate !== undefined && toTime !== undefined) {
			try {
				onApply({
					fromDateTime: new Date(`${fromDate} ${fromTime}`),
					toDateTime: new Date(`${toDate} ${toTime}`),
				});
			} catch (e) {
				logger.error(e);
			}
		}
	};

	return (
		<Form onSubmit={handleApply} initialValues={defaultValues}>
			{({handleSubmit, form, values}) => {
				const selectedRange: DateRange = {
					from: parseCompleteDate(values.fromDate),
					to: parseCompleteDate(values.toDate),
				};

				return (
					<Dialog
						open={isModalOpen}
						onOpenChange={(open) => {
							if (!open) {
								onCancel();
							}
						}}
					>
						<DialogContent data-testid="date-range-modal" size="md" aria-describedby={undefined}>
							<DialogHeader>
								<DialogTitle>{title}</DialogTitle>
							</DialogHeader>
							<DialogBody>
								<div className="flex flex-col gap-6 sm:flex-row sm:items-start">
									<Calendar
										mode="range"
										numberOfMonths={1}
										fixedWeeks
										showOutsideDays
										// Without this, react-day-picker v10 treats a click against an existing
										// complete range as moving one boundary instead of starting a fresh
										// selection, so a single click would otherwise fill both dates/times
										// at once (enabling Apply) instead of requiring a second click like
										// the Carbon picker did.
										resetOnSelect
										selected={selectedRange}
										defaultMonth={selectedRange.from}
										onSelect={(range) => {
											if (range?.from) {
												form.change('fromDate', formatDate(range.from));
												if (form.getFieldState('fromTime')?.value === '') {
													form.change('fromTime', defaultTime.from);
												}
											}
											if (range?.to) {
												form.change('toDate', formatDate(range.to));
												if (form.getFieldState('toTime')?.value === '') {
													form.change('toTime', defaultTime.to);
												}
											} else {
												// `resetOnSelect` reports a fresh selection as `{from, to: undefined}`;
												// without clearing these, the stale end date/time from the previous
												// range would combine with the new start into a bogus complete range.
												form.change('toDate', '');
												form.change('toTime', '');
											}
										}}
									/>
									<div className="flex flex-col gap-6 sm:w-64">
										<div className="flex flex-col gap-3">
											<DateInput type="from" labelText="From date" />
											<DateInput type="to" labelText="To date" />
										</div>
										<div className="flex gap-3">
											<TimeInput type="from" labelText="From time" />
											<TimeInput type="to" labelText="To time" />
										</div>
									</div>
								</div>
							</DialogBody>
							<DialogFooter>
								<Button type="button" variant="secondary" onClick={onCancel}>
									Cancel
								</Button>
								<Button
									type="button"
									onClick={handleSubmit}
									disabled={
										!form.getFieldState('fromDate')?.value ||
										!form.getFieldState('fromTime')?.value ||
										!form.getFieldState('toDate')?.value ||
										!form.getFieldState('toTime')?.value ||
										form.getState().invalid
									}
								>
									Apply
								</Button>
							</DialogFooter>
						</DialogContent>
					</Dialog>
				);
			}}
		</Form>
	);
};

export {DateRangeModal};
