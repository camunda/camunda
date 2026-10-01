/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Tooltip, TooltipContent, TooltipTrigger} from '@camunda/design-system';
import {Check, CircleAlert, CircleDashed, Clock} from '@camunda/design-system/icons';
import {cn} from '#/shared/cn';

const formatCount = (count: number): string =>
	Intl.NumberFormat('en', {notation: 'compact', maximumFractionDigits: 1}).format(count);

type Props = {
	totalCount: number;
	completedCount: number;
	failedCount: number;
};

const BatchItemsCount: React.FC<Props> = ({totalCount, completedCount, failedCount}) => {
	const pendingCount = totalCount - completedCount - failedCount;
	const hasAnyProgress = completedCount > 0 || failedCount > 0;

	if (!hasAnyProgress) {
		const description = pendingCount > 0 ? 'not started' : 'no items';

		return (
			<Tooltip>
				<TooltipTrigger asChild>
					<span
						aria-label={description}
						className="flex min-w-12 cursor-default items-center gap-2 text-neutral-foreground-subtle"
					>
						<CircleDashed aria-hidden="true" focusable="false" className="h-4 w-4 shrink-0" />0
					</span>
				</TooltipTrigger>
				<TooltipContent side="bottom">{description}</TooltipContent>
			</Tooltip>
		);
	}

	const statusConfig = [
		{
			key: 'successful',
			count: completedCount,
			label: 'successful',
			Icon: Check,
			className: 'text-success-foreground-strong',
		},
		{
			key: 'failed',
			count: failedCount,
			label: 'failed',
			Icon: CircleAlert,
			className: 'text-danger-foreground-strong',
		},
		{
			key: 'pending',
			count: pendingCount,
			label: 'pending',
			Icon: Clock,
			className: 'text-neutral-foreground-subtle',
		},
	] as const;

	return (
		<div className="flex items-center gap-4">
			{statusConfig
				.filter(({count}) => count > 0)
				.map(({key, count, label, Icon, className}) => {
					const description = `${count.toLocaleString()} ${label}`;

					return (
						<Tooltip key={key}>
							<TooltipTrigger asChild>
								<span
									role="status"
									aria-label={description}
									className="flex min-w-12 cursor-default items-center gap-2"
								>
									<Icon aria-hidden="true" focusable="false" className={cn('h-4 w-4 shrink-0', className)} />
									{formatCount(count)}
								</span>
							</TooltipTrigger>
							<TooltipContent side="bottom">{description}</TooltipContent>
						</Tooltip>
					);
				})}
		</div>
	);
};

export {BatchItemsCount};
