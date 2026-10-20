/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo} from 'react';
import {useTranslation} from 'react-i18next';
import {Card, CardDescription, CardHeader, CardTitle} from '@camunda/design-system';
import type {ElementInstance} from '@camunda/camunda-api-zod-schemas/8.11';
import {BPMNDiagram, type DiagramMarker} from '#/tasklist/modules/task-details/components/process-diagram/BPMNDiagram';

function getMarkers(elementInstances: ElementInstance[]): DiagramMarker[] {
	const markers = new Map<string, DiagramMarker['variant']>();

	for (const {elementId, state, hasIncident} of elementInstances) {
		if (state === 'ACTIVE') {
			markers.set(elementId, hasIncident ? 'incident' : 'active');
		} else if (state === 'COMPLETED' && !markers.has(elementId)) {
			markers.set(elementId, 'completed');
		}
	}

	return Array.from(markers, ([elementId, variant]) => ({elementId, variant}));
}

type Props = {
	xml: string;
	elementInstances: ElementInstance[];
};

/** The simplified view of a case-marked process that is outside the case contract (CASE_UI.md). */
const CaseDiagramCard: React.FC<Props> = ({xml, elementInstances}) => {
	const {t} = useTranslation();
	const markers = useMemo(() => getMarkers(elementInstances), [elementInstances]);

	return (
		<Card className="gap-0 overflow-hidden pb-0">
			<CardHeader>
				<CardTitle>{t('tasklist.caseDiagramTitle')}</CardTitle>
				<CardDescription>{t('tasklist.caseDiagramDescription')}</CardDescription>
			</CardHeader>
			<div className="flex h-96 flex-col px-5 py-4">
				<BPMNDiagram xml={xml} markers={markers} />
			</div>
		</Card>
	);
};

export {CaseDiagramCard};
