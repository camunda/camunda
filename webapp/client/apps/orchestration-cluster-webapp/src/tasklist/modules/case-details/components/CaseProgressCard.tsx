/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo} from 'react';
import {useTranslation} from 'react-i18next';
import {useNavigate} from '@tanstack/react-router';
import {Card, CardAction, CardHeader, CardTitle, ToggleGroup, ToggleGroupItem} from '@camunda/design-system';
import type {ProcessDefinitionStatistic} from '@camunda/camunda-api-zod-schemas/8.11';
import {CaseWaitsPanel} from '#/tasklist/modules/case-details/components/CaseWaitsPanel';
import {MilestoneProgress} from '#/tasklist/modules/case-details/components/MilestoneProgress';
import type {CaseWait} from '#/tasklist/modules/case-details/getCaseWaits';
import type {MilestoneProgress as Milestone} from '#/tasklist/modules/case-details/getMilestoneProgress';
import {isProgressView, type ProgressView} from '#/tasklist/modules/case-details/searchSchema';
import {BPMNDiagram, type DiagramMarker} from '#/tasklist/modules/task-details/components/process-diagram/BPMNDiagram';

function getMarkers(statistics: ProcessDefinitionStatistic[]): DiagramMarker[] {
	return statistics.flatMap(({elementId, active, completed, incidents}): DiagramMarker[] => {
		if (incidents > 0) {
			return [{elementId, variant: 'incident'}];
		}

		if (active > 0) {
			return [{elementId, variant: 'active'}];
		}

		return completed > 0 ? [{elementId, variant: 'completed'}] : [];
	});
}

type Props = {
	milestones: Milestone[];
	waits: CaseWait[];
	xml: string;
	statistics: ProcessDefinitionStatistic[];
	progressView: ProgressView;
};

const CaseProgressCard: React.FC<Props> = ({milestones, waits, xml, statistics, progressView}) => {
	const {t} = useTranslation();
	const navigate = useNavigate();
	const markers = useMemo(() => getMarkers(statistics), [statistics]);

	return (
		<Card className="gap-0 overflow-hidden pb-0">
			<CardHeader>
				<CardTitle>{t('tasklist.caseDetailsProgressTitle')}</CardTitle>
				<CardAction>
					<ToggleGroup
						type="single"
						variant="outline"
						size="sm"
						value={progressView}
						aria-label={t('tasklist.caseDetailsProgressViewLabel')}
						onValueChange={(value) => {
							if (isProgressView(value)) {
								navigate({to: '.', search: (previousSearch) => ({...previousSearch, progressView: value})});
							}
						}}
					>
						<ToggleGroupItem value="stages">{t('tasklist.caseDetailsProgressStages')}</ToggleGroupItem>
						<ToggleGroupItem value="diagram">{t('tasklist.caseDetailsProgressDiagram')}</ToggleGroupItem>
					</ToggleGroup>
				</CardAction>
			</CardHeader>
			{progressView === 'stages' ? (
				<div className="px-5 py-5">
					{milestones.length === 0 ? (
						<p className="text-sm text-neutral-foreground-subtle">{t('tasklist.caseDetailsNoMilestones')}</p>
					) : (
						<MilestoneProgress milestones={milestones} />
					)}
				</div>
			) : (
				<div className="flex h-96 flex-col px-5 py-4">
					<BPMNDiagram xml={xml} markers={markers} />
				</div>
			)}
			<CaseWaitsPanel waits={waits} />
		</Card>
	);
};

export {CaseProgressCard};
