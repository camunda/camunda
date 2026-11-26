/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useMemo} from 'react';
import {useTranslation} from 'react-i18next';
import {useField, useForm} from 'react-final-form';
import {isValid, parseISO} from 'date-fns';
import {DateTimeRangePicker, DEFAULT_DATE_TIME_RANGE_PRESETS} from '@camunda/design-system';
import {formatISODate} from '#/operate/shared/DateRangeField/formatDate';
import {getCurrentDateLocale} from '#/shared/i18n';
import {getDateTimeRangePickerMessages} from './getDateTimeRangePickerMessages';
import {PRESET_LABELS} from './presetLabels';

const ISO_VALUE = /^\d{4}-\d{2}-\d{2}T/;

const toDate = (isoValue: string | undefined) => {
	if (isoValue === undefined || !ISO_VALUE.test(isoValue)) {
		return undefined;
	}
	const date = parseISO(isoValue);
	return isValid(date) ? date : undefined;
};

type Props = {
	id: string;
	label: string;
	fromKey: string;
	toKey: string;
};

const DateTimeRangePickerField: React.FC<Props> = ({id, label, fromKey, toKey}) => {
	const {t} = useTranslation();
	const form = useForm();
	const validateRange = (_: string | undefined, allValues: object) => {
		const values = allValues as Record<string, string | undefined>;
		const fromDate = toDate(values[fromKey]);
		const toDateValue = toDate(values[toKey]);
		return fromDate !== undefined && toDateValue !== undefined && toDateValue < fromDate ? 'endBeforeStart' : undefined;
	};
	const {input: fromInput} = useField<string | undefined>(fromKey, {validate: validateRange});
	const fromValue = fromInput.value;
	const {input: toInput} = useField<string | undefined>(toKey, {validate: validateRange});
	const toValue = toInput.value;
	const value = useMemo(() => ({from: toDate(fromValue), to: toDate(toValue)}), [fromValue, toValue]);
	useEffect(() => {
		if (fromValue && toDate(fromValue) === undefined) {
			form.change(fromKey, undefined);
		}
		if (toValue && toDate(toValue) === undefined) {
			form.change(toKey, undefined);
		}
	}, [form, fromKey, fromValue, toKey, toValue]);
	const presets = useMemo(
		() =>
			DEFAULT_DATE_TIME_RANGE_PRESETS.map((preset) => {
				const getLabel = PRESET_LABELS[preset.id];
				return getLabel === undefined ? preset : {...preset, label: getLabel(t)};
			}),
		[t],
	);

	const locale = getCurrentDateLocale();
	const messages = getDateTimeRangePickerMessages({t, includeQuickRanges: true});

	return (
		<DateTimeRangePicker
			id={id}
			aria-label={label}
			value={value}
			presets={presets}
			locale={locale}
			messages={messages}
			onChange={(range) => {
				form.change(fromKey, range.from ? formatISODate(range.from) : undefined);
				form.change(toKey, range.to ? formatISODate(range.to) : undefined);
			}}
		/>
	);
};

export {DateTimeRangePickerField};
