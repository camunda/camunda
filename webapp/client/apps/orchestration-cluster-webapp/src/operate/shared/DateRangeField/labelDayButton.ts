/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {format} from 'date-fns';
import type {CalendarProps} from '@camunda/design-system';

type LabelDayButton = NonNullable<NonNullable<CalendarProps['labels']>['labelDayButton']>;

// react-day-picker's default label uses an ordinal day ("January 1st") that doesn't contain the visible
// day number as a word, which fails WCAG 2.5.3 (Label in Name). Use the cardinal day instead.
const labelDayButton: LabelDayButton = (date, modifiers, options, dateLib) => {
	const pattern = 'EEEE, d MMMM yyyy';
	let label = dateLib ? dateLib.format(date, pattern) : format(date, pattern, options);
	if (modifiers.today) {
		label = `Today, ${label}`;
	}
	if (modifiers.selected) {
		label = `${label}, selected`;
	}
	return label;
};

export {labelDayButton};
