/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {untruncateJson} from './untruncateJson';

function beautifyTruncatedJSON(value: string) {
	try {
		const {completed, collectionDepth} = untruncateJson(value);
		const pretty = JSON.stringify(JSON.parse(completed), null, '\t');
		let end = pretty.length;
		for (let depth = 0; depth < collectionDepth; depth++) {
			while (end > 0 && /\s/.test(pretty[end - 1] ?? '')) {
				end--;
			}
			if (pretty[end - 1] !== ']' && pretty[end - 1] !== '}') {
				break;
			}
			end--;
		}
		return pretty.slice(0, end);
	} catch {
		return value;
	}
}

export {beautifyTruncatedJSON};
