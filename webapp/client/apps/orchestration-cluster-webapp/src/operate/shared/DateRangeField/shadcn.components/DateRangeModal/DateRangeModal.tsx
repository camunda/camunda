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
import {isValid} from 'date-fns';
import {logger} from '#/operate/shared/utils/logger';
import {parseDate} from '#/operate/shared/utils/parseDate';
import {formatDate} from '../../formatDate';
import {DateInput} from './DateInput';
import {TimeInput} from './TimeInput';

const defaultTime = {
	from: '00:00:00',
	to: '23:59:59',
};

// Only complete, calendar-valid dates may reach the Calendar: a partial value ("2022-0"),
// or a well-shaped but out-of-range one ("2022-02-31"), parsed via `parseDate` would yield
// an Invalid Date, which crashes react-day-picker's range selection.
const isCompleteDate = (date: string | undefined): date is string =>
	/^\d{4}-\d{1,2}-\d{1,2}$/.test(date ?? '') && isValid(parseDate(date ?? ''));

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
					from: isCompleteDate(values.fromDate) ? parseDate(values.fromDate) : undefined,
					to: isCompleteDate(values.toDate) ? parseDate(values.toDate) : undefined,
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
						<DialogContent data-testid="date-range-modal" size="sm" aria-describedby={undefined}>
							<DialogHeader>
								<DialogTitle>{title}</DialogTitle>
							</DialogHeader>
							<DialogBody>
								<div className="flex flex-col gap-6">
									<Calendar
										mode="range"
										numberOfMonths={1}
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
											}
										}}
									/>
									<div className="flex flex-col gap-3">
										<DateInput type="from" labelText="From date" />
										<DateInput type="to" labelText="To date" />
									</div>
									<div className="flex gap-px">
										<TimeInput type="from" labelText="From time" />
										<TimeInput type="to" labelText="To time" />
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
