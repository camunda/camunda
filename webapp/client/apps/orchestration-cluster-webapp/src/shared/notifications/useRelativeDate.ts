/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {differenceInHours, differenceInSeconds, format, formatDistanceToNowStrict} from 'date-fns';
import {getCurrentDateLocale} from '#/shared/i18n/index';
import {useEffect, useState} from 'react';
import {t} from 'i18next';

function getRelativeDate(date: number): string {
	if (differenceInSeconds(Date.now(), date) <= 10) {
		return t('relativeDateJustNow');
	}

	if (differenceInHours(date, Date.now()) > 0) {
		return format(new Date(date), 'dd MMM yyyy - p', {
			locale: getCurrentDateLocale(),
		});
	}

	return formatDistanceToNowStrict(date, {locale: getCurrentDateLocale()});
}

function useRelativeDate(targetDate: number): string {
	const [relativeDate, setRelativeDate] = useState(getRelativeDate(targetDate));

	useEffect(() => {
		const intervalID = setInterval(() => {
			setRelativeDate(getRelativeDate(targetDate));
		}, 1000);

		return () => {
			clearInterval(intervalID);
		};
	}, [targetDate]);

	return relativeDate;
}

export {useRelativeDate};
