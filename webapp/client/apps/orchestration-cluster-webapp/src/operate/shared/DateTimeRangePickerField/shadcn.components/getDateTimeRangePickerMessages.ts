/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {TFunction} from 'i18next';
import {getTimeZoneLabel} from '#/operate/shared/DateRangeField/getTimeZoneLabel';

type Props = {
	t: TFunction;
	includeQuickRanges?: boolean;
};

const getDateTimeRangePickerMessages = ({t, includeQuickRanges = false}: Props) => {
	const messages = {
		placeholder: t('operate.shared.dateRangeField.enterDateRange'),
		fromTime: t('operate.shared.dateRangeField.fromTime'),
		toTime: t('operate.shared.dateRangeField.toTime'),
		timeZone: t('operate.shared.dateTimeRangePicker.timeZone', {timeZone: getTimeZoneLabel()}),
		endBeforeStart: t('operate.shared.dateTimeRangePicker.endBeforeStart'),
		apply: t('operate.shared.dateRangeField.apply'),
		cancel: t('operate.shared.dateRangeField.cancel'),
		clearRange: t('operate.shared.dateTimeRangePicker.clearRange'),
		selectEndDate: t('operate.shared.dateTimeRangePicker.selectEndDate'),
		pickDayFirst: t('operate.shared.dateTimeRangePicker.pickDayFirst'),
		pickEndDay: t('operate.shared.dateTimeRangePicker.selectEndDate'),
	};

	if (!includeQuickRanges) {
		return messages;
	}

	return {
		...messages,
		quickRanges: t('operate.shared.dateTimeRangePicker.quickRanges'),
	};
};

export {getDateTimeRangePickerMessages};
