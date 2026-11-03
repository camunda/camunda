/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {BusinessObject} from 'bpmn-js/lib/NavigatedViewer';
import type {ElementInstance} from '@camunda/camunda-api-zod-schemas/8.11';
import {StateIcon} from '#/operate/shared/StateIcon/shadcn.components/StateIcon';
import {formatTimestamp} from '#/operate/shared/utils/formatTimestamp';
import {ElementInstanceIcon} from './ElementInstanceIcon';

type Props = {
	item: ElementInstance;
	businessObject?: BusinessObject;
	suffix?: string;
	showTimestamp?: boolean;
};

const HistoryRowLabel: React.FC<Props> = ({item, businessObject, suffix, showTimestamp = true}) => {
	const name = item.elementName ?? item.elementId;
	return (
		<span className="flex min-w-0 flex-1 items-center gap-2">
			<ElementInstanceIcon businessObject={businessObject} root={item.type === 'PROCESS'} />
			<StateIcon state={item.hasIncident ? 'INCIDENT' : item.state} size={16} />
			<span className="min-w-0 flex-1 truncate">
				{name}
				{suffix}
			</span>
			{showTimestamp && item.endDate && (
				<span className="shrink-0 text-xs text-muted-foreground">{formatTimestamp(item.endDate)}</span>
			)}
		</span>
	);
};

export {HistoryRowLabel};
