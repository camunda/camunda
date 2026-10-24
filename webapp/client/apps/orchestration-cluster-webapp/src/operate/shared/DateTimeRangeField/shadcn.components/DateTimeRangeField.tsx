/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useRef, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {useField, useForm} from 'react-final-form';
import {format, isValid, parse, subMinutes} from 'date-fns';
import {
	Calendar,
	Input,
	Label,
	Popover,
	PopoverAnchor,
	PopoverContent,
	Select,
	SelectContent,
	SelectItem,
	SelectTrigger,
	SelectValue,
} from '@camunda/design-system';
import {formatISODate} from '#/operate/shared/DateRangeField/formatDate';
import {getTimeZoneLabel} from '#/operate/shared/DateRangeField/getTimeZoneLabel';

const VALUE_FORMAT = 'yyyy-MM-dd HH:mm:ss';
// Stored bounds are ISO strings; anything else is malformed text kept so submitting stays blocked
const ISO_VALUE = /^\d{4}-\d{2}-\d{2}T/;

const QUICK_RANGES = [
	{unit: 'Minutes', count: 5, minutes: 5},
	{unit: 'Minutes', count: 15, minutes: 15},
	{unit: 'Minutes', count: 30, minutes: 30},
	{unit: 'Hours', count: 1, minutes: 60},
	{unit: 'Hours', count: 3, minutes: 180},
	{unit: 'Hours', count: 6, minutes: 360},
	{unit: 'Hours', count: 12, minutes: 720},
	{unit: 'Hours', count: 24, minutes: 1440},
	{unit: 'Days', count: 2, minutes: 2880},
	{unit: 'Days', count: 7, minutes: 10080},
	{unit: 'Days', count: 30, minutes: 43200},
] as const;

const parseText = (text: string) => {
	const parsed = parse(text, VALUE_FORMAT, new Date());
	return isValid(parsed) ? parsed : undefined;
};

const toDate = (isoValue: string | undefined) => {
	if (isoValue === undefined || isoValue === '') {
		return undefined;
	}
	const date = new Date(isoValue);
	return isValid(date) ? date : undefined;
};

type DateTimeFieldProps = {
	id: string;
	label: string;
	name: string;
	defaultTime: string;
	isRequired: boolean;
	otherName: string;
	// Set on the end field, which reports when it lies before the start
	startName?: string;
	onManualChange: () => void;
};

const DateTimeField: React.FC<DateTimeFieldProps> = ({
	id,
	label,
	name,
	defaultTime,
	isRequired,
	otherName,
	startName,
	onManualChange,
}) => {
	const {t} = useTranslation();
	const form = useForm();
	// Blocks the form submit while the text is not a date, the other bound is set but this one is empty, or the end lies before the start
	const validateBound = (bound: string | undefined, allValues: object) => {
		const values = allValues as Record<string, string | undefined>;
		if (bound !== undefined && bound !== '' && (!ISO_VALUE.test(bound) || toDate(bound) === undefined)) {
			return 'invalidDateTime';
		}
		if ((bound === undefined || bound === '') && (values[otherName] ?? '') !== '') {
			return 'requiredDateTime';
		}
		const startDate = startName === undefined ? undefined : toDate(values[startName]);
		const endDate = toDate(bound);
		return startDate !== undefined && endDate !== undefined && endDate < startDate ? 'endBeforeStart' : undefined;
	};
	const value = useField<string | undefined>(name, {validate: validateBound}).input.value;
	const startValue = useField<string | undefined>(startName ?? name).input.value;
	const [open, setOpen] = useState(false);
	// base is the form value the draft belongs to; a different value means the form was reinitialized, e.g. by history navigation
	const [draftState, setDraftState] = useState<{text: string; base: string} | undefined>();
	const draft = draftState !== undefined && draftState.base === (value ?? '') ? draftState.text : undefined;
	const setDraft = (text: string | undefined, base: string = value ?? '') =>
		setDraftState(text === undefined ? undefined : {text, base});
	const [isFocused, setIsFocused] = useState(false);
	const anchorRef = useRef<HTMLDivElement>(null);
	const contentRef = useRef<HTMLDivElement>(null);
	const skipOpenOnFocusRef = useRef(false);
	const date = toDate(value);
	const time = date ? format(date, 'HH:mm:ss') : defaultTime;
	const isDraftInvalid = draft !== undefined && draft !== '' && parseText(draft) === undefined;
	const isMissing = isRequired && date === undefined && (draft === undefined || draft === '');
	const start = startName === undefined ? undefined : toDate(startValue);
	const isBeforeStart = start !== undefined && date !== undefined && date < start;
	const error =
		isFocused || open
			? undefined
			: isDraftInvalid
				? t('operate.shared.dateTimeRangeField.invalidDateTime')
				: isMissing
					? t('operate.shared.dateTimeRangeField.requiredDateTime')
					: isBeforeStart
						? t('operate.shared.dateTimeRangeField.endBeforeStart')
						: undefined;

	const commit = (nextDate: Date | undefined) => {
		onManualChange();
		form.change(name, nextDate ? formatISODate(nextDate) : undefined);
	};

	return (
		<div className="grid grid-cols-[5.5rem_1fr] items-start gap-x-2">
			<Label htmlFor={id} className="h-9 font-normal text-neutral-foreground-subtle">
				{label}
			</Label>
			<Popover open={open} onOpenChange={setOpen}>
				<PopoverAnchor asChild>
					<div ref={anchorRef}>
						<Input
							id={id}
							placeholder="YYYY-MM-DD HH:mm:ss"
							autoComplete="off"
							value={draft ?? (date ? format(date, VALUE_FORMAT) : '')}
							aria-invalid={error !== undefined}
							invalidText={error}
							announceInvalidText
							onFocus={() => {
								setIsFocused(true);
								if (skipOpenOnFocusRef.current) {
									skipOpenOnFocusRef.current = false;
									return;
								}
								setOpen(true);
							}}
							onKeyDown={(event) => {
								if (event.key === 'ArrowDown') {
									event.preventDefault();
									setOpen(true);
									requestAnimationFrame(() =>
										contentRef.current?.querySelector<HTMLElement>('[role="grid"] button[tabindex="0"]')?.focus(),
									);
								}
							}}
							onClick={() => setOpen(true)}
							onBlur={() => {
								setIsFocused(false);
								if (!isDraftInvalid) {
									setDraft(undefined);
								}
							}}
							onChange={(event) => {
								const text = event.target.value;
								const parsed = text === '' ? undefined : parseText(text);
								if (text === '' || parsed) {
									setDraft(text, parsed ? formatISODate(parsed) : '');
									commit(parsed);
									return;
								}
								// The malformed text replaces the stored bound, so the previous valid date cannot be submitted
								setDraft(text, text);
								onManualChange();
								form.change(name, text);
							}}
						/>
					</div>
				</PopoverAnchor>
				<PopoverContent
					ref={contentRef}
					aria-label={label}
					className="w-auto"
					onEscapeKeyDown={() => {
						const input = anchorRef.current?.querySelector('input');
						if (input && document.activeElement !== input) {
							skipOpenOnFocusRef.current = true;
							input.focus();
						}
					}}
					align="start"
					onOpenAutoFocus={(event) => event.preventDefault()}
					onCloseAutoFocus={(event) => event.preventDefault()}
					onInteractOutside={(event) => {
						if (anchorRef.current?.contains(event.target as Node)) {
							event.preventDefault();
						}
					}}
				>
					<div className="flex flex-col gap-4">
						<Calendar
							mode="single"
							selected={date}
							defaultMonth={date}
							onSelect={(selected) => {
								if (selected) {
									setDraft(undefined);
									commit(parse(`${format(selected, 'yyyy-MM-dd')} ${time}`, VALUE_FORMAT, new Date()));
								}
							}}
							showOutsideDays
							fixedWeeks
							className="p-0"
						/>
						<div className="flex flex-col gap-1.5">
							<Label htmlFor={`${id}-time`}>{t('operate.shared.dateTimeRangeField.time')}</Label>
							<Input
								id={`${id}-time`}
								type="time"
								step={1}
								value={time}
								onChange={(event) => {
									const chosen = event.target.value.length === 5 ? `${event.target.value}:00` : event.target.value;
									const next = parse(
										`${format(date ?? new Date(), 'yyyy-MM-dd')} ${chosen}`,
										'yyyy-MM-dd HH:mm:ss',
										new Date(),
									);
									if (isValid(next)) {
										setDraft(undefined);
										commit(next);
									}
								}}
							/>
						</div>
					</div>
				</PopoverContent>
			</Popover>
		</div>
	);
};

type Props = {
	idPrefix: string;
	fromKey: string;
	toKey: string;
};

const DateTimeRangeField: React.FC<Props> = ({idPrefix, fromKey, toKey}) => {
	const {t} = useTranslation();
	const form = useForm();
	const fromValue = useField<string | undefined>(fromKey).input.value;
	const toValue = useField<string | undefined>(toKey).input.value;
	const hasFrom = fromValue !== '';
	const hasTo = toValue !== '';
	const [quickRangeState, setQuickRangeState] = useState<{minutes: string; from: string; to: string} | null>(null);
	// Back/Forward navigation can replace the bounds, so the preset only shows while they still match what it set
	const quickRange =
		quickRangeState !== null && quickRangeState.from === fromValue && quickRangeState.to === toValue
			? quickRangeState.minutes
			: '';
	const [draftResetKey, setDraftResetKey] = useState(0);

	const handleQuickRange = (minutes: string) => {
		const now = new Date();
		const from = formatISODate(subMinutes(now, Number(minutes)));
		const to = formatISODate(now);
		form.change(fromKey, from);
		form.change(toKey, to);
		setQuickRangeState({minutes, from, to});
		setDraftResetKey((key) => key + 1);
	};

	return (
		<div className="flex flex-col gap-3">
			<DateTimeField
				key={`from-${draftResetKey}`}
				id={`${idPrefix}-from`}
				label={t('operate.shared.dateTimeRangeField.from')}
				name={fromKey}
				defaultTime="00:00:00"
				isRequired={hasTo}
				otherName={toKey}
				onManualChange={() => setQuickRangeState(null)}
			/>
			<DateTimeField
				key={`to-${draftResetKey}`}
				id={`${idPrefix}-to`}
				label={t('operate.shared.dateTimeRangeField.to')}
				name={toKey}
				defaultTime="23:59:59"
				startName={fromKey}
				isRequired={hasFrom}
				otherName={fromKey}
				onManualChange={() => setQuickRangeState(null)}
			/>
			<div className="grid grid-cols-[5.5rem_1fr] items-start gap-x-2">
				<Label htmlFor={`${idPrefix}-quick-range`} className="h-9 font-normal text-neutral-foreground-subtle">
					{t('operate.shared.dateTimeRangeField.quickRanges')}
				</Label>
				<Select value={quickRange} onValueChange={handleQuickRange}>
					<SelectTrigger id={`${idPrefix}-quick-range`} className="w-full">
						<SelectValue placeholder={t('operate.shared.dateTimeRangeField.selectQuickRange')} />
					</SelectTrigger>
					<SelectContent>
						{QUICK_RANGES.map(({unit, count, minutes}) => (
							<SelectItem key={minutes} value={String(minutes)}>
								{t(`operate.shared.dateTimeRangeField.last${unit}`, {count})}
							</SelectItem>
						))}
					</SelectContent>
				</Select>
			</div>
			<p className="text-xs text-muted-foreground">
				{t('operate.shared.dateTimeRangeField.timeZone', {timeZone: getTimeZoneLabel()})}
			</p>
		</div>
	);
};

export {DateTimeRangeField};
