/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {useQuery} from '@tanstack/react-query';
import {AccordionItem, Tag} from '@carbon/react';
import {DiffEditor} from '#/operate/shared/Editors/DiffEditor';
import {beautifyJSON} from '#/shared/json/beautifyJSON';
import {formatTimestamp} from '#/operate/shared/utils/formatTimestamp';
import {spaceAndCapitalize} from '#/operate/shared/utils/spaceAndCapitalize';
import {variableHistoryItemQuery, type VariableHistoryItem} from './variableHistory.queries';
import {Actor, EntryTitle, ValueComparison, ValueLabel} from './variables.styled';

type Props = {
	item: VariableHistoryItem;
};

function actorLabel({actorType, actorId, agentElementId}: VariableHistoryItem) {
	if (actorType === 'ENGINE') {
		return 'Engine';
	}
	if (agentElementId !== null) {
		return `AI agent ${agentElementId} via client ${actorId}`;
	}
	return actorId ?? spaceAndCapitalize(actorType);
}

const ACTOR_TAG_TYPES: Record<string, 'blue' | 'purple' | 'teal'> = {
	USER: 'blue',
	CLIENT: 'purple',
	ENGINE: 'teal',
};

const LINE_HEIGHT = 20;
const MAX_VISIBLE_LINES = 14;

function editorHeight(...values: string[]) {
	const lines = Math.max(...values.map((value) => value.split('\n').length));
	return `${Math.min(lines, MAX_VISIBLE_LINES) * LINE_HEIGHT + LINE_HEIGHT}px`;
}

const VariableHistoryEntry: React.FC<Props> = ({item}) => {
	const [isOpen, setIsOpen] = useState(false);
	const {data} = useQuery({...variableHistoryItemQuery(item.variableHistoryItemKey), enabled: isOpen});
	const detail = data === undefined ? item : {...item, value: data.value};
	const previousValue = detail.previousValue === null ? '' : beautifyJSON(detail.previousValue);
	const newValue = beautifyJSON(detail.value);
	const previousLabel =
		detail.previousValue !== null
			? 'Previous value'
			: item.operationType === 'CREATED'
				? 'Initial value'
				: 'Previous value (not recorded)';
	const isRedacted = item.metadata.protectionModes.includes('REDACT');

	return (
		<AccordionItem
			onHeadingClick={({isOpen}) => setIsOpen(isOpen)}
			title={
				<EntryTitle>
					<Actor>{actorLabel(item)}</Actor>
					<Tag size="sm" type={ACTOR_TAG_TYPES[item.actorType] ?? 'gray'}>
						{spaceAndCapitalize(item.actorType)}
					</Tag>
					<Tag size="sm" type="gray">
						{spaceAndCapitalize(item.operationType)}
					</Tag>
					<Tag size="sm" type="outline">
						{item.scopeKey === item.processInstanceKey ? 'Global' : 'Local'}
					</Tag>
					{item.elementId !== null && <span>{item.elementId}</span>}
					<time dateTime={item.timestamp}>{formatTimestamp(item.timestamp)}</time>
				</EntryTitle>
			}
		>
			{!isOpen ? null : isRedacted ? (
				<ValueLabel>Redacted</ValueLabel>
			) : (
				<ValueComparison>
					<div>
						<ValueLabel>{previousLabel}</ValueLabel>
						<ValueLabel>New value</ValueLabel>
					</div>
					<DiffEditor
						originalValue={previousValue}
						modifiedValue={newValue}
						height={editorHeight(previousValue, newValue)}
					/>
				</ValueComparison>
			)}
		</AccordionItem>
	);
};

export {VariableHistoryEntry};
