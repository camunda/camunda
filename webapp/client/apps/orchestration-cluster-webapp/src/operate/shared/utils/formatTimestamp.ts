/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {format, parseISO} from 'date-fns';

function formatTimestamp(timestamp: string | null | undefined, placeholder = '--'): string {
	return timestamp ? format(parseISO(timestamp), 'yyyy-MM-dd HH:mm:ss') : placeholder;
}

export {formatTimestamp};
