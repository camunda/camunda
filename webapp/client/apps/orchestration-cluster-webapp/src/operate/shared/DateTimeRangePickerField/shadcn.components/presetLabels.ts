/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {TFunction} from 'i18next';

const PRESET_LABELS: Record<string, (t: TFunction) => string> = {
	'5m': (t) => t('operate.shared.dateTimeRangePicker.lastMinutes', {count: 5}),
	'15m': (t) => t('operate.shared.dateTimeRangePicker.lastMinutes', {count: 15}),
	'30m': (t) => t('operate.shared.dateTimeRangePicker.lastMinutes', {count: 30}),
	'1h': (t) => t('operate.shared.dateTimeRangePicker.lastHours', {count: 1}),
	'3h': (t) => t('operate.shared.dateTimeRangePicker.lastHours', {count: 3}),
	'6h': (t) => t('operate.shared.dateTimeRangePicker.lastHours', {count: 6}),
	'12h': (t) => t('operate.shared.dateTimeRangePicker.lastHours', {count: 12}),
	'24h': (t) => t('operate.shared.dateTimeRangePicker.lastHours', {count: 24}),
	'2d': (t) => t('operate.shared.dateTimeRangePicker.lastDays', {count: 2}),
	'7d': (t) => t('operate.shared.dateTimeRangePicker.lastDays', {count: 7}),
	'30d': (t) => t('operate.shared.dateTimeRangePicker.lastDays', {count: 30}),
};

export {PRESET_LABELS};
