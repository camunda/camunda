/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import type {StageProgress, StageState} from '@camunda/case-management-sdk';
import {Circle, CircleAlert, CircleCheck, CircleDot, CircleMinus, CircleX} from '@camunda/design-system/icons';
import {cn} from '#/shared/cn';
import {formatISODateTime} from '#/tasklist/modules/dates/formatDateRelative';

// The design system's precompiled stylesheet only ships the token utilities it uses itself,
// so the action colors are referenced through their CSS variables. Danger uses the border token for
// icons and lines, and the subtle foreground for text: both stay red in the light and the dark theme.
const DANGER_ICON = 'text-[color:var(--danger-border-strong)]';
const DANGER_TEXT = 'text-danger-foreground-subtle';

const STATE_MAPPINGS = {
	done: {
		icon: CircleCheck,
		iconClassName: 'text-[color:var(--success-action-default)]',
		labelClassName: 'text-neutral-foreground-strong',
		statusClassName: 'text-neutral-foreground-subtle',
		labelKey: 'tasklist.caseStageDone',
	},
	inProgress: {
		icon: CircleDot,
		iconClassName: 'text-[color:var(--info-action-default)]',
		labelClassName: 'font-semibold text-[color:var(--info-action-default)]',
		statusClassName: 'text-[color:var(--info-action-default)]',
		labelKey: 'tasklist.caseStageInProgress',
	},
	incident: {
		icon: CircleAlert,
		iconClassName: DANGER_ICON,
		labelClassName: `font-semibold ${DANGER_TEXT}`,
		statusClassName: DANGER_TEXT,
		labelKey: 'tasklist.caseStageIncident',
	},
	cancelled: {
		icon: CircleX,
		iconClassName: DANGER_ICON,
		labelClassName: 'text-neutral-foreground-subtle',
		statusClassName: 'text-neutral-foreground-subtle',
		labelKey: 'tasklist.caseStageCancelled',
	},
	skipped: {
		icon: CircleMinus,
		iconClassName: 'text-neutral-foreground-subtle',
		labelClassName: 'text-neutral-foreground-subtle line-through',
		statusClassName: 'text-neutral-foreground-subtle',
		labelKey: 'tasklist.caseStageSkipped',
	},
	ahead: {
		icon: Circle,
		iconClassName: 'text-neutral-foreground-subtle',
		labelClassName: 'text-neutral-foreground-subtle',
		statusClassName: 'text-neutral-foreground-subtle',
		labelKey: 'tasklist.caseStageAhead',
	},
} as const satisfies Record<
	StageState,
	{icon: unknown; iconClassName: string; labelClassName: string; statusClassName: string; labelKey: string}
>;

const REACHED_STATES = new Set<StageState>(['done', 'skipped', 'cancelled', 'inProgress', 'incident']);
const PASSED_STATES = new Set<StageState>(['done', 'skipped', 'cancelled']);
const REACHED_CONNECTOR = 'bg-[color:var(--success-action-default)]';
const PENDING_CONNECTOR = 'bg-border';
// An alternative stage cancelled the planned stages: the line turns red from the cancelled stage on.
const CANCELLED_CONNECTOR = 'bg-[color:var(--danger-border-strong)]';
const ALTERNATIVE_CLASS_NAMES = {
	iconClassName: DANGER_ICON,
	labelClassName: `font-semibold ${DANGER_TEXT}`,
	statusClassName: DANGER_TEXT,
};

function getConnector(isAfterCancellation: boolean, isReached: boolean): string {
	if (isAfterCancellation) {
		return CANCELLED_CONNECTOR;
	}

	return isReached ? REACHED_CONNECTOR : PENDING_CONNECTOR;
}

function getTime(isoDate: string | null): string | null {
	return isoDate === null ? null : (formatISODateTime(isoDate)?.relative.text ?? null);
}

type Props = {
	progress: StageProgress[];
};

const StageLine: React.FC<Props> = ({progress}) => {
	const {t} = useTranslation();
	const cancelledIndex = progress.findIndex(({state}) => state === 'cancelled');
	const isAfterCancellation = (index: number) => cancelledIndex >= 0 && index >= cancelledIndex;

	return (
		<ol className="flex items-start" aria-label={t('tasklist.caseStagesLabel')}>
			{progress.map(({stage, state, startDate, endDate}, index) => {
				const isActiveAlternative = stage.kind === 'alternative' && state !== 'cancelled';
				const {icon: Icon, labelKey, ...stateClassNames} = STATE_MAPPINGS[state];
				const {iconClassName, labelClassName, statusClassName} = isActiveAlternative
					? ALTERNATIVE_CLASS_NAMES
					: stateClassNames;
				const previous = progress[index - 1];
				const isFirst = index === 0;
				const isLast = index === progress.length - 1;
				const isReachedFromPrevious =
					previous !== undefined && PASSED_STATES.has(previous.state) && REACHED_STATES.has(state);
				const time = state === 'done' ? getTime(endDate) : getTime(startDate);

				return (
					<li
						key={stage.id}
						className="flex min-w-0 flex-1 flex-col items-center gap-1.5 text-center"
						aria-current={state === 'inProgress' || state === 'incident' ? 'step' : undefined}
					>
						<div className="flex w-full items-center" aria-hidden>
							<span
								className={cn(
									'h-0.5 flex-1 rounded-full',
									isFirst ? 'bg-transparent' : getConnector(isAfterCancellation(index - 1), isReachedFromPrevious),
								)}
							/>
							<Icon className={cn('mx-1.5 size-6 shrink-0', iconClassName)} />
							<span
								className={cn(
									'h-0.5 flex-1 rounded-full',
									isLast ? 'bg-transparent' : getConnector(isAfterCancellation(index), PASSED_STATES.has(state)),
								)}
							/>
						</div>
						<span className={cn('max-w-full truncate px-2 text-sm', labelClassName)} title={stage.name}>
							{stage.name}
						</span>
						<span className={cn('max-w-full truncate px-2 text-xs', statusClassName)}>
							{state === 'done' && time !== null ? time : t(labelKey)}
						</span>
					</li>
				);
			})}
		</ol>
	);
};

export {StageLine};
