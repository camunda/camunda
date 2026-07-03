/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {format, parseISO} from 'date-fns';
import {getCurrentDateLocale} from '#/shared/i18n';

function formatHistoryDate(timestamp: string) {
	return format(parseISO(timestamp), 'dd MMM yyyy - p', {locale: getCurrentDateLocale()});
}

export {formatHistoryDate};
