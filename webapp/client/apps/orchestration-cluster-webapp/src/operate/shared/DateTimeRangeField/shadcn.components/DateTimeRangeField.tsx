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
	onManualChange: () => void;
};

const DateTimeField: React.FC<DateTimeFieldProps> = ({id, label, name, defaultTime, isRequired, onManualChange}) => {
	const {t} = useTranslation();
	const form = useForm();
	const value = useField<string | undefined>(name).input.value;
	const [open, setOpen] = useState(false);
	const [draft, setDraft] = useState<string | undefined>();
	const [isFocused, setIsFocused] = useState(false);
	const anchorRef = useRef<HTMLDivElement>(null);
	const contentRef = useRef<HTMLDivElement>(null);
	const skipOpenOnFocusRef = useRef(false);
	const date = toDate(value);
	const time = date ? format(date, 'HH:mm:ss') : defaultTime;
	const isDraftInvalid = draft !== undefined && draft !== '' && parseText(draft) === undefined;
	const isMissing = isRequired && date === undefined && (draft === undefined || draft === '');
	const error =
		isFocused || open
			? undefined
			: isDraftInvalid
				? t('operate.shared.dateTimeRangeField.invalidDateTime')
				: isMissing
					? t('operate.shared.dateTimeRangeField.requiredDateTime')
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
								setDraft(text);
								if (text === '') {
									commit(undefined);
								} else {
									const parsed = parseText(text);
									if (parsed) {
										commit(parsed);
									}
								}
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
									const next = parse(
										`${format(date ?? new Date(), 'yyyy-MM-dd')} ${event.target.value}`,
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
	const hasFrom = useField<string | undefined>(fromKey).input.value !== '';
	const hasTo = useField<string | undefined>(toKey).input.value !== '';
	const [quickRange, setQuickRange] = useState('');
	const [draftResetKey, setDraftResetKey] = useState(0);

	const handleQuickRange = (minutes: string) => {
		const now = new Date();
		form.change(fromKey, formatISODate(subMinutes(now, Number(minutes))));
		form.change(toKey, formatISODate(now));
		setQuickRange(minutes);
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
				onManualChange={() => setQuickRange('')}
			/>
			<DateTimeField
				key={`to-${draftResetKey}`}
				id={`${idPrefix}-to`}
				label={t('operate.shared.dateTimeRangeField.to')}
				name={toKey}
				defaultTime="23:59:59"
				isRequired={hasFrom}
				onManualChange={() => setQuickRange('')}
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
