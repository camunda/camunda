/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {typographyVariants} from '@camunda/design-system';
import {CircleAlert, CircleCheck, CircleDot, CircleHelp, CirclePause, CircleX} from '@camunda/design-system/icons';
import type {DecisionInstanceState, ProcessInstanceState} from '@camunda/camunda-api-zod-schemas/8.11';
import {cn} from '#/shared/cn';

type State = ProcessInstanceState | DecisionInstanceState | 'INCIDENT';

// Inlined 1:1 equivalent of the Carbon `StateIcon` (`#/operate/shared/StateIcon/StateIcon.tsx`),
// which is out of this ticket's scope and still Carbon-only — a shadcn file must never import it
// (even transitively), so the state-to-icon mapping is reproduced here with DS icons instead.
const STATE_ICONS = {
	FAILED: {Icon: CircleAlert, className: 'text-danger-foreground-strong'},
	INCIDENT: {Icon: CircleAlert, className: 'text-danger-foreground-strong'},
	ACTIVE: {Icon: CircleDot, className: 'text-success-foreground-strong'},
	COMPLETED: {Icon: CircleCheck, className: 'text-neutral-foreground'},
	EVALUATED: {Icon: CircleCheck, className: 'text-neutral-foreground'},
	SUSPENDED: {Icon: CirclePause, className: 'text-neutral-foreground-subtle'},
	TERMINATED: {Icon: CircleX, className: ''},
	UNSPECIFIED: {Icon: CircleHelp, className: ''},
	UNKNOWN: {Icon: CircleHelp, className: ''},
} as const satisfies Record<State, {Icon: React.ComponentType<React.SVGProps<SVGSVGElement>>; className: string}>;

type Column = {
	title?: string;
	content: React.ReactNode;
	dataTestId?: string;
	hideOverflowingContent?: boolean;
	hidden?: boolean;
};

type InstanceHeaderProps = {
	state: State;
	instanceName: string;
	incidentsCount?: number;
	nameSubtitle?: React.ReactNode;
	headerColumns: string[];
	bodyColumns: Column[];
	additionalContent?: React.ReactNode;
	hideBottomBorder?: boolean;
};

const InstanceHeader: React.FC<InstanceHeaderProps> = ({
	state,
	headerColumns,
	bodyColumns,
	instanceName,
	incidentsCount = 0,
	nameSubtitle,
	additionalContent,
	hideBottomBorder = false,
}) => {
	const {t} = useTranslation();
	const {Icon: StateIcon, className: stateIconClassName} = STATE_ICONS[state];
	const hasSubtitleRow = incidentsCount > 0 || Boolean(nameSubtitle);

	return (
		<header
			data-testid="instance-header"
			className={cn(
				'flex min-w-0 items-center gap-4 border-b border-[var(--neutral-border-subtle)] bg-neutral-background-subtle px-4 py-1',
				hideBottomBorder && 'border-b-0',
			)}
		>
			<StateIcon
				data-testid={`${state}-icon`}
				className={cn('size-6 shrink-0', stateIconClassName)}
				aria-hidden="true"
			/>

			<div title={instanceName} className="mr-4 flex min-w-0 shrink flex-col gap-0.5">
				<span
					className={cn(
						'truncate text-neutral-foreground',
						typographyVariants({variant: hasSubtitleRow ? 'label-sm' : 'label-md'}),
					)}
				>
					{instanceName}
				</span>
				{hasSubtitleRow && (
					<div className="flex min-w-0 items-center gap-2">
						{incidentsCount > 0 && (
							<span className={cn('truncate text-danger-foreground-strong', typographyVariants({variant: 'label-md'}))}>
								{t('operate.shared.instanceHeader.incidentsCount', {count: incidentsCount})}
							</span>
						)}
						{nameSubtitle && (
							<span
								className={cn(
									'inline-flex shrink-0 items-center self-start rounded-full bg-warning-background-strong px-3 py-1 whitespace-nowrap text-black',
									typographyVariants({variant: 'label-sm'}),
									'font-semibold',
								)}
							>
								{nameSubtitle}
							</span>
						)}
					</div>
				)}
			</div>

			<table className="table-fixed border-separate border-spacing-y-0.5 text-neutral-foreground [&_td:not(:first-child)]:pl-8 [&_th:not(:first-child)]:pl-8">
				<thead>
					<tr>
						{headerColumns.map((column, index) => (
							<th key={index} className={cn('truncate text-left', typographyVariants({variant: 'label-sm'}))}>
								{column}
							</th>
						))}
					</tr>
				</thead>
				<tbody>
					<tr>
						{bodyColumns.map((column, index) => {
							if (column.hidden) {
								return null;
							}
							const hideOverflowingContent = column.hideOverflowingContent ?? true;
							return (
								<td
									key={index}
									title={column.title}
									data-testid={column.dataTestId}
									className={cn(typographyVariants({variant: 'label-md'}), hideOverflowingContent && 'truncate')}
								>
									{column.content}
								</td>
							);
						})}
					</tr>
				</tbody>
			</table>
			<div className="ml-auto shrink-0">{additionalContent}</div>
		</header>
	);
};

export {InstanceHeader};
