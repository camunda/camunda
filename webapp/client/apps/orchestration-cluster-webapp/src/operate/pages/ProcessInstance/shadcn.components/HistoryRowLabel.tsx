/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
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

const STATE_LABEL_KEYS = {
	FAILED: 'operate.shared.stateIcon.failed',
	INCIDENT: 'operate.shared.stateIcon.incident',
	ACTIVE: 'operate.shared.stateIcon.active',
	COMPLETED: 'operate.shared.stateIcon.completed',
	EVALUATED: 'operate.shared.stateIcon.evaluated',
	SUSPENDED: 'operate.shared.stateIcon.suspended',
	TERMINATED: 'operate.shared.stateIcon.terminated',
	UNSPECIFIED: 'operate.shared.stateIcon.unspecified',
	UNKNOWN: 'operate.shared.stateIcon.unknown',
} as const;

const HISTORY_TREE_CLASS_NAME = '[&_[role=treeitem]]:border-l-[2rem] [&_[role=treeitem]]:border-transparent';

const HistoryRowLabel: React.FC<Props> = ({item, businessObject, suffix, showTimestamp = true}) => {
	const {t} = useTranslation();
	const name = item.elementName ?? item.elementId;
	const state = item.hasIncident ? 'INCIDENT' : item.state;
	return (
		<span className="flex min-w-0 flex-1 items-center gap-2">
			<span className="absolute inset-y-0 -left-6 flex items-center">
				<StateIcon state={state} size={16} />
			</span>
			<ElementInstanceIcon businessObject={businessObject} root={item.type === 'PROCESS'} />
			<span className="sr-only">{t(STATE_LABEL_KEYS[state])}</span>
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

export {HistoryRowLabel, HISTORY_TREE_CLASS_NAME};
