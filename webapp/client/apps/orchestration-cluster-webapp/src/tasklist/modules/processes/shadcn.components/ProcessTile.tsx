/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {ProcessDefinition} from '@camunda/camunda-api-zod-schemas/8.10';
import {Badge, Button, Card, CardContent, Heading} from '@camunda/design-system';
import {ArrowRight, List} from 'lucide-react';
import {useTranslation} from 'react-i18next';
import type {StartProcessStatus} from '#/tasklist/modules/processes/startProcessMachine';

type Props = {
	process: ProcessDefinition;
	status: StartProcessStatus;
	isStartButtonDisabled: boolean;
	onStartProcess: () => void;
};

function getStartProcessStatusLabel(t: (key: string) => string, status: StartProcessStatus) {
	if (status === 'active') {
		return t('tasklist.processesStartProcessPendingStatusText');
	}

	if (status === 'active-tasks') {
		return t('tasklist.processesStartProcessWaitForTasksText');
	}

	if (status === 'finished') {
		return t('tasklist.processesStartProcessSuccess');
	}

	if (status === 'error') {
		return t('tasklist.processesStartProcessFailed');
	}

	return undefined;
}

const ProcessTile: React.FC<Props> = ({process, status, isStartButtonDisabled, onStartProcess}) => {
	const {t} = useTranslation();
	const displayName = process.name ?? process.processDefinitionId;
	const isBusy = status === 'active' || status === 'active-tasks';
	const isStatusVisible = status !== 'inactive';
	const statusLabel = getStartProcessStatusLabel(t, status);

	return (
		<Card className="h-full" data-testid={`process-tile-${process.processDefinitionKey}`}>
			<CardContent className="flex h-full min-w-0 flex-col justify-between gap-6">
				<div className="flex min-w-0 flex-col gap-1">
					<Heading as="h2" variant="heading-sm" className="truncate" title={displayName}>
						{displayName}
					</Heading>
					<span
						className="block min-h-4 truncate text-xs leading-4 text-neutral-foreground-subtle"
						title={process.processDefinitionId}
					>
						{displayName === process.processDefinitionId ? '' : process.processDefinitionId}
					</span>
				</div>
				<div className="flex flex-wrap items-center gap-2">
					<Button
						type="button"
						variant="secondary"
						size="sm"
						loading={isBusy}
						disabled={isStartButtonDisabled}
						aria-disabled={isStatusVisible || undefined}
						aria-live={status === 'error' || status === 'finished' ? 'assertive' : 'polite'}
						onClick={isStatusVisible ? undefined : onStartProcess}
					>
						{statusLabel ?? t('tasklist.processesTileStartProcessButtonLabel')}
						{statusLabel === undefined && process.hasStartForm ? (
							<ArrowRight aria-hidden className="ml-1 size-4" />
						) : null}
					</Button>
					{process.hasStartForm ? (
						<Badge className="w-fit gap-1" title={t('tasklist.processesProcessTileAttributes')}>
							<List aria-hidden className="size-3" />
							{t('tasklist.processesProcessTileAttributeRequiresForm')}
						</Badge>
					) : null}
				</div>
			</CardContent>
		</Card>
	);
};

export {ProcessTile};
