/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Tooltip, TooltipContent, TooltipTrigger} from '@camunda/design-system';
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

const wrapperFontClassNames: Record<Size, string> = {
	small: 'text-sm leading-[18px] text-neutral-foreground-subtle',
	medium: 'text-sm font-semibold leading-[18px] text-foreground',
	large: 'text-base font-semibold leading-6 text-foreground',
};

// Typography only — color is computed separately so it isn't a Tailwind class
// merge relying on `text-danger-foreground-strong` winning over these.
const labelTypographyClassNames: Record<'small' | 'medium', string> = {
	small: 'text-sm leading-[18px]',
	medium: 'text-sm font-semibold leading-[18px]',
};

const barHeightClassNames: Record<Size, string> = {
	small: 'h-0.5',
	medium: 'h-1',
	large: 'h-1.5',
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
			<div className={cn('flex', wrapperFontClassNames[size])}>
				<div
					data-testid="incident-instances-badge"
					className={cn('min-w-10', hasIncidents ? 'text-danger-foreground-strong' : 'text-neutral-foreground-subtle')}
				>
					{incidentsCount}
				</div>
				{label && (
					<div
						className={cn(
							'flex-1 overflow-hidden text-ellipsis whitespace-nowrap',
							labelTypographyClassNames[label.size],
							labelTextColorClassName,
						)}
					>
						{label.text}
					</div>
				)}
				{(showDrainingIndicator || showActiveInstancesCount) && (
					<div className="ml-auto flex items-center gap-2 pl-4">
						{showDrainingIndicator && (
							// Relies on the app-level `<TooltipProvider>` rendered once in
							// `Header.tsx` — nesting another one here would create a
							// duplicate provider and make Radix's open-state tracking
							// misfire (see `@camunda/design-system`'s tooltip.tsx comment).
							<Tooltip>
								<TooltipTrigger asChild>
									<div data-testid="draining-indicator" className="flex items-center text-danger-foreground-strong">
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
									'ml-auto w-[139px] text-right',
									hasActiveInstances ? 'text-success-foreground-strong' : 'text-foreground',
								)}
							>
								{activeInstancesCount}
							</div>
						)}
					</div>
				)}
			</div>
			{showIncidentsBar && (
				<div data-testid="instances-bar" className="relative my-2">
					<div
						className={cn(barHeightClassNames[size], hasActiveInstances ? 'bg-success-background-strong' : 'bg-border')}
					/>
					<div
						className={cn(barHeightClassNames[size], 'absolute top-0 bg-danger-background-strong')}
						style={{width: `${incidentsBarRatio}%`}}
					/>
				</div>
			)}
		</div>
	);
};

export {InstancesBar};
