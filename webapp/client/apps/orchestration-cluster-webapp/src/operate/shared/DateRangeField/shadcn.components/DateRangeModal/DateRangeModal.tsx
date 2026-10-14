/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
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

const getTimeZoneLabel = () => {
	const {timeZone} = Intl.DateTimeFormat().resolvedOptions();
	const offset = new Intl.DateTimeFormat(undefined, {timeZoneName: 'longOffset'})
		.formatToParts(new Date())
		.find(({type}) => type === 'timeZoneName')?.value;
	return offset === undefined ? timeZone : `${timeZone} (${offset})`;
};

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
	const {t} = useTranslation();
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
										className="-mt-2"
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
									<div className="flex min-w-0 flex-1 flex-col gap-6">
										<div className="grid grid-cols-2 gap-3">
											<DateInput type="from" labelText={t('operate.shared.dateRangeField.fromDate')} />
											<DateInput type="to" labelText={t('operate.shared.dateRangeField.toDate')} />
										</div>
										<div className="flex flex-col gap-2">
											<div className="grid grid-cols-2 gap-3">
												<TimeInput type="from" labelText={t('operate.shared.dateRangeField.fromTime')} />
												<TimeInput type="to" labelText={t('operate.shared.dateRangeField.toTime')} />
											</div>
											<p className="text-xs text-muted-foreground">
												{t('operate.shared.dateRangeField.timeZone', {timeZone: getTimeZoneLabel()})}
											</p>
										</div>
									</div>
								</div>
							</DialogBody>
							<DialogFooter>
								<Button type="button" variant="secondary" onClick={onCancel}>
									{t('operate.shared.dateRangeField.cancel')}
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
									{t('operate.shared.dateRangeField.apply')}
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
