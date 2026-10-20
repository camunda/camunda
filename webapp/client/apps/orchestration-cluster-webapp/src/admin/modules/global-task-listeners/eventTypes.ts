/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {TFunction} from 'i18next';
import type {GlobalTaskListenerEventType} from '@camunda/camunda-api-zod-schemas/8.11';

const LISTENER_EVENT_TYPES: GlobalTaskListenerEventType[] = [
	'all',
	'creating',
	'assigning',
	'updating',
	'completing',
	'canceling',
];

const INDIVIDUAL_EVENT_TYPES = LISTENER_EVENT_TYPES.filter((eventType) => eventType !== 'all');

function getEventTypeLabel(eventType: GlobalTaskListenerEventType, t: TFunction): string {
	const labels: Record<GlobalTaskListenerEventType, string> = {
		all: t('admin.globalTaskListeners.eventTypeAll'),
		creating: t('admin.globalTaskListeners.eventTypeCreating'),
		assigning: t('admin.globalTaskListeners.eventTypeAssigning'),
		updating: t('admin.globalTaskListeners.eventTypeUpdating'),
		completing: t('admin.globalTaskListeners.eventTypeCompleting'),
		canceling: t('admin.globalTaskListeners.eventTypeCanceling'),
	};
	return labels[eventType];
}

function getEventTypeLabels(eventTypes: GlobalTaskListenerEventType[], t: TFunction): string {
	return eventTypes.includes('all')
		? t('admin.globalTaskListeners.eventTypeAll')
		: eventTypes.map((eventType) => getEventTypeLabel(eventType, t)).join(', ');
}

function getEventTypeOptions(t: TFunction) {
	return LISTENER_EVENT_TYPES.map((eventType) => ({value: eventType, label: getEventTypeLabel(eventType, t)}));
}

/**
 * Keeps the "all" option in lockstep with the individual ones: toggling it selects or clears
 * every event type, and checking the last individual type checks "all" alongside them.
 *
 * `selected` is `MultiSelect`'s raw `string[]`. Filtering `LISTENER_EVENT_TYPES` by it both
 * narrows the type and restores the schema's order, which carries meaning here — "all" is a real
 * API value listed among the individual events, not a select-all affordance.
 */
function syncAllEventType(selected: string[], previous: GlobalTaskListenerEventType[]): GlobalTaskListenerEventType[] {
	const next = LISTENER_EVENT_TYPES.filter((eventType) => selected.includes(eventType));

	if (next.includes('all') !== previous.includes('all')) {
		return next.includes('all') ? [...LISTENER_EVENT_TYPES] : [];
	}

	const individual = next.filter((eventType) => eventType !== 'all');
	return individual.length === INDIVIDUAL_EVENT_TYPES.length ? [...LISTENER_EVENT_TYPES] : individual;
}

/**
 * "all" already means every event type to the API, so it is sent on its own rather than next to
 * the individual values the form keeps checked to render them as selected.
 */
function toRequestEventTypes(eventTypes: GlobalTaskListenerEventType[]): GlobalTaskListenerEventType[] {
	return eventTypes.includes('all') ? ['all'] : eventTypes.filter((eventType) => eventType !== 'all');
}

/**
 * Counterpart to `toRequestEventTypes`: a listener stored as `["all"]` is expanded into every
 * event type, because that is the state the form keeps once "All events" is checked — see
 * {@link syncAllEventType}. Without expanding, the loaded state differs from the one any
 * interaction produces: the dropdown shows "All events" alone, and checking a single event on top
 * of it drops "all" and leaves that one event as the whole selection.
 */
function toFormEventTypes(eventTypes: GlobalTaskListenerEventType[]): GlobalTaskListenerEventType[] {
	return eventTypes.includes('all')
		? [...LISTENER_EVENT_TYPES]
		: LISTENER_EVENT_TYPES.filter((eventType) => eventTypes.includes(eventType));
}

export {getEventTypeLabels, getEventTypeOptions, syncAllEventType, toFormEventTypes, toRequestEventTypes};
