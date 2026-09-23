/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Text, Tooltip, TooltipContent, TooltipTrigger, type TypographyVariant} from '@camunda/design-system';
import {Timer} from '@camunda/design-system/icons';
import {cn} from '#/shared/cn';

type Size = 'small' | 'medium' | 'large';

type Props = {
	label?: {
		type: 'process' | 'incident';
		size: 'small' | 'medium';
		text: string;
	};
	activeInstancesCount?: number;
	incidentsCount: number;
	isDraining?: boolean;
	drainingDescription?: string;
	size: Size;
	className?: string;
};

const wrapperVariant: Record<Size, TypographyVariant> = {
	small: 'label-md',
	medium: 'label-md-strong',
	large: 'body-lg',
};

const labelVariant: Record<'small' | 'medium', TypographyVariant> = {
	small: 'label-md',
	medium: 'label-md-strong',
};

const barHeightClassNames: Record<Size, string> = {
	small: 'h-0.5',
	medium: 'h-1',
	large: 'h-2',
};

const InstancesBar: React.FC<Props> = ({
	label,
	activeInstancesCount,
	incidentsCount,
	isDraining = false,
	drainingDescription,
	size,
	className,
}) => {
	const total = (activeInstancesCount ?? 0) + incidentsCount;
	const incidentsBarRatio = total === 0 ? 0 : (100 * incidentsCount) / total;
	const hasIncidents = incidentsCount > 0;
	const hasActiveInstances = (activeInstancesCount ?? 0) > 0;
	const showIncidentsBar = activeInstancesCount !== undefined;
	const showActiveInstancesCount = activeInstancesCount !== undefined && activeInstancesCount >= 0;
	const showDrainingIndicator = isDraining && drainingDescription !== undefined;
	const isLabelRed = label !== undefined && label.type === 'incident' && size === 'medium';
	const labelTextColorClassName = isLabelRed
		? 'text-danger-foreground-strong'
		: label?.size === 'medium'
			? 'text-foreground'
			: 'text-neutral-foreground-subtle';

	return (
		<div className={className}>
			<Text as="div" variant={wrapperVariant[size]} className="flex">
				<div
					data-testid="incident-instances-badge"
					className={cn(
						'min-w-12 tabular-nums',
						hasIncidents ? 'text-danger-foreground-strong' : 'text-neutral-foreground-subtle',
					)}
				>
					{incidentsCount}
				</div>
				{label && (
					<div className="flex min-w-0 flex-1 items-center gap-2">
						<Tooltip>
							<TooltipTrigger asChild>
								<Text
									as="div"
									variant={labelVariant[label.size]}
									className={cn('min-w-0 overflow-hidden text-ellipsis whitespace-nowrap', labelTextColorClassName)}
								>
									{label.text}
								</Text>
							</TooltipTrigger>
							<TooltipContent>{label.text}</TooltipContent>
						</Tooltip>
						{showDrainingIndicator && (
							<Tooltip>
								<TooltipTrigger asChild>
									<div
										data-testid="draining-indicator"
										tabIndex={0}
										className="flex shrink-0 items-center text-danger-foreground-strong"
									>
										<Timer className="h-4 w-4" aria-hidden="true" />
									</div>
								</TooltipTrigger>
								<TooltipContent>{drainingDescription}</TooltipContent>
							</Tooltip>
						)}
					</div>
				)}
				{!label && (showDrainingIndicator || showActiveInstancesCount) && (
					<div className="ml-auto flex items-center gap-2 pl-4">
						{showDrainingIndicator && (
							<Tooltip>
								<TooltipTrigger asChild>
									<div
										data-testid="draining-indicator"
										tabIndex={0}
										className="flex items-center text-danger-foreground-strong"
									>
										<Timer className="h-4 w-4" aria-hidden="true" />
									</div>
								</TooltipTrigger>
								<TooltipContent>{drainingDescription}</TooltipContent>
							</Tooltip>
						)}
						{showActiveInstancesCount && (
							<div
								data-testid="active-instances-badge"
								className={cn(
									'ml-auto min-w-[6ch] text-right tabular-nums',
									hasActiveInstances ? 'text-success-foreground-strong' : 'text-foreground',
								)}
							>
								{activeInstancesCount}
							</div>
						)}
					</div>
				)}
				{label && showActiveInstancesCount && (
					<div
						data-testid="active-instances-badge"
						className={cn(
							'ml-auto min-w-[6ch] pl-4 text-right tabular-nums',
							hasActiveInstances ? 'text-success-foreground-strong' : 'text-foreground',
						)}
					>
						{activeInstancesCount}
					</div>
				)}
			</Text>
			{showIncidentsBar && (
				<div data-testid="instances-bar" className="relative my-2">
					<div
						className={cn(barHeightClassNames[size], !hasActiveInstances && 'bg-border')}
						style={hasActiveInstances ? {backgroundColor: 'var(--success-foreground-strong)'} : undefined}
					/>
					<div
						className={cn(barHeightClassNames[size], 'absolute top-0')}
						style={{width: `${incidentsBarRatio}%`, backgroundColor: 'var(--danger-foreground-strong)'}}
					/>
				</div>
			)}
		</div>
	);
};

export {InstancesBar};
