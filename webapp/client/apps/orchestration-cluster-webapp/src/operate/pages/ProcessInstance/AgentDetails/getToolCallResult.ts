/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {AgentInstanceMessageContent} from '@camunda/camunda-api-zod-schemas/8.11';

type ToolCallResult = {
	value: string;
	language: 'markdown' | 'json';
	preview: string;
};

function getToolCallResult(content: AgentInstanceMessageContent[]): ToolCallResult | null {
	const entry = content.find((item) => item.contentType === 'TEXT' || item.contentType === 'OBJECT');

	switch (entry?.contentType) {
		case 'TEXT':
			return {value: entry.text, language: 'markdown', preview: entry.text};
		case 'OBJECT':
			return {
				value: JSON.stringify(entry.object, null, 2),
				language: 'json',
				preview: JSON.stringify(entry.object),
			};
		default:
			return null;
	}
}

export {getToolCallResult};
