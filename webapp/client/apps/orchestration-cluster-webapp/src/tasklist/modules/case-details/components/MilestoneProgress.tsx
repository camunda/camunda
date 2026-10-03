/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {Circle, CircleCheck, CircleDot, CircleMinus, CircleAlert} from '@camunda/design-system/icons';
import type {
	MilestoneProgress as Milestone,
	MilestoneState,
} from '#/tasklist/modules/case-details/getMilestoneProgress';
import {formatISODateTime} from '#/tasklist/modules/dates/formatDateRelative';
import {cn} from '#/shared/cn';

// The design system's precompiled stylesheet only ships the token utilities it uses itself,
// so the action colors are referenced through their CSS variables.
const STATE_MAPPINGS = {
	completed: {
		icon: CircleCheck,
		iconClassName: 'text-[color:var(--success-action-default)]',
		labelClassName: 'text-neutral-foreground-strong',
		statusClassName: 'text-neutral-foreground-subtle',
		labelKey: 'tasklist.caseDetailsMilestoneCompleted',
	},
	active: {
		icon: CircleDot,
		iconClassName: 'text-[color:var(--info-action-default)]',
		labelClassName: 'font-semibold text-neutral-foreground-strong',
		statusClassName: 'text-[color:var(--info-action-default)]',
		labelKey: 'tasklist.caseDetailsMilestoneActive',
	},
	blocked: {
		icon: CircleAlert,
		iconClassName: 'text-[color:var(--danger-action-default)]',
		labelClassName: 'font-semibold text-neutral-foreground-strong',
		statusClassName: 'text-[color:var(--danger-action-default)]',
		labelKey: 'tasklist.caseDetailsMilestoneBlocked',
	},
	skipped: {
		icon: CircleMinus,
		iconClassName: 'text-neutral-foreground-subtle',
		labelClassName: 'text-neutral-foreground-subtle line-through',
		statusClassName: 'text-neutral-foreground-subtle',
		labelKey: 'tasklist.caseDetailsMilestoneSkipped',
	},
	notStarted: {
		icon: Circle,
		iconClassName: 'text-neutral-foreground-subtle',
		labelClassName: 'text-neutral-foreground-subtle',
		statusClassName: 'text-neutral-foreground-subtle',
		labelKey: 'tasklist.caseDetailsMilestoneNotStarted',
	},
} as const satisfies Record<
	MilestoneState,
	{icon: unknown; iconClassName: string; labelClassName: string; statusClassName: string; labelKey: string}
>;

const REACHED_STATES = new Set<MilestoneState>(['completed', 'skipped', 'active', 'blocked']);
const PASSED_STATES = new Set<MilestoneState>(['completed', 'skipped']);
const REACHED_CONNECTOR = 'bg-[color:var(--success-action-default)]';
const PENDING_CONNECTOR = 'bg-border';

function getRelativeDate(isoDate: string | null): string | null {
	return isoDate === null ? null : (formatISODateTime(isoDate)?.relative.text ?? null);
}

type Props = {
	milestones: Milestone[];
};

const MilestoneProgress: React.FC<Props> = ({milestones}) => {
	const {t} = useTranslation();

	return (
		<ol className="flex items-start" aria-label={t('tasklist.caseDetailsMilestonesLabel')}>
			{milestones.map((milestone, index) => {
				const {icon: Icon, iconClassName, labelClassName, statusClassName, labelKey} = STATE_MAPPINGS[milestone.state];
				const previous = milestones[index - 1];
				const isFirst = index === 0;
				const isLast = index === milestones.length - 1;
				const isReachedFromPrevious =
					previous !== undefined && PASSED_STATES.has(previous.state) && REACHED_STATES.has(milestone.state);
				const date =
					milestone.state === 'completed' ? getRelativeDate(milestone.endDate) : getRelativeDate(milestone.startDate);

				return (
					<li
						key={milestone.id}
						className="flex min-w-0 flex-1 flex-col items-center gap-1.5 text-center"
						aria-current={milestone.state === 'active' || milestone.state === 'blocked' ? 'step' : undefined}
					>
						<div className="flex w-full items-center" aria-hidden>
							<span
								className={cn(
									'h-0.5 flex-1 rounded-full',
									isFirst ? 'bg-transparent' : isReachedFromPrevious ? REACHED_CONNECTOR : PENDING_CONNECTOR,
								)}
							/>
							<Icon className={cn('mx-1.5 size-5 shrink-0', iconClassName)} />
							<span
								className={cn(
									'h-0.5 flex-1 rounded-full',
									isLast
										? 'bg-transparent'
										: PASSED_STATES.has(milestone.state)
											? REACHED_CONNECTOR
											: PENDING_CONNECTOR,
								)}
							/>
						</div>
						<span className={cn('max-w-full truncate px-2 text-sm', labelClassName)} title={milestone.label}>
							{milestone.label}
						</span>
						<span className={cn('max-w-full truncate px-2 text-xs', statusClassName)}>
							{date === null ? t(labelKey) : `${t(labelKey)} · ${date}`}
						</span>
					</li>
				);
			})}
		</ol>
	);
};

export {MilestoneProgress};
