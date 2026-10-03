/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo} from 'react';
import {useTranslation} from 'react-i18next';
import {Badge} from '@camunda/design-system';
import type {ElementInstanceInspection} from '@camunda/camunda-api-zod-schemas/8.11';
import {LabelWithTooltip} from '#/tasklist/modules/available-tasks/components/LabelWithTooltip';
import {WaitStateIcon} from '#/tasklist/modules/cases/components/WaitStateIcon';
import {
	getWaitStateLabel,
	groupWaitStateLabelsByKind,
	type WaitStateGroup,
	type WaitStateLabel,
} from '#/tasklist/modules/cases/getWaitStateLabel';
import {cn} from '#/shared/cn';

const EMPTY_CELL = '-';

const PrimaryWaitState: React.FC<{waitState: WaitStateLabel}> = ({waitState}) => {
	const isStuck = waitState.kind === 'stuck';
	const extras = waitState.extras.join(' · ');

	return (
		<div className="flex min-w-0 items-center gap-1.5" title={[waitState.label, waitState.detail, extras].join(' · ')}>
			<WaitStateIcon kind={waitState.kind} label={waitState.label} />
			<span
				className={cn(
					'truncate text-sm font-medium',
					isStuck ? 'text-danger-foreground-strong' : 'text-neutral-foreground-strong',
				)}
			>
				{waitState.detail}
			</span>
			{extras === '' ? null : (
				<span
					className={cn(
						'truncate text-xs',
						isStuck ? 'text-danger-foreground-subtle' : 'text-neutral-foreground-subtle',
					)}
				>
					{extras}
				</span>
			)}
		</div>
	);
};

const WaitStateKindChip: React.FC<{group: WaitStateGroup}> = ({group}) => {
	const {t} = useTranslation();
	const accessibleLabel = t('tasklist.casesWaitKindCount', {label: group.label, count: group.waitStates.length});

	return (
		<LabelWithTooltip
			title={accessibleLabel}
			content={
				<div className="flex flex-col gap-1">
					<span className="font-medium">{group.label}</span>
					<ul className="flex flex-col gap-0.5">
						{group.waitStates.map((waitState) => (
							<li key={waitState.id}>{[waitState.detail, ...waitState.extras].join(' · ')}</li>
						))}
					</ul>
				</div>
			}
			align="top-start"
		>
			<Badge variant={group.kind === 'stuck' ? 'danger' : 'neutral'} aria-label={accessibleLabel}>
				<WaitStateIcon kind={group.kind} isColored={false} />
				{group.waitStates.length}
			</Badge>
		</LabelWithTooltip>
	);
};

type Props = {
	waitStates: ElementInstanceInspection[];
};

const WaitStateList: React.FC<Props> = ({waitStates}) => {
	const groups = useMemo(() => groupWaitStateLabelsByKind(waitStates.map(getWaitStateLabel)), [waitStates]);
	const primaryWaitState = groups[0]?.waitStates[0];

	if (primaryWaitState === undefined) {
		return <span className="text-neutral-foreground-subtle">{EMPTY_CELL}</span>;
	}

	return (
		<div className="flex min-w-0 items-center gap-3">
			<PrimaryWaitState waitState={primaryWaitState} />
			{waitStates.length > 1 ? (
				<div className="flex shrink-0 items-center gap-1">
					{groups.map((group) => (
						<WaitStateKindChip key={group.id} group={group} />
					))}
				</div>
			) : null}
		</div>
	);
};

export {WaitStateList};
