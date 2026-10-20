/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {Tooltip, TooltipContent, TooltipTrigger} from '@camunda/design-system';
import {CircleAlert, CircleCheck, CircleDashed, Clock} from '@camunda/design-system/icons';
import {cn} from '#/shared/cn';

type Props = {
	totalCount: number;
	completedCount: number;
	failedCount: number;
};

const BatchItemsCount: React.FC<Props> = ({totalCount, completedCount, failedCount}) => {
	const {t, i18n} = useTranslation();
	// Compact for the rendered count (e.g. "1.2K"); full precision for the accessible label/tooltip.
	const formatCompactCount = (count: number): string =>
		Intl.NumberFormat(i18n.language, {notation: 'compact', maximumFractionDigits: 1}).format(count);
	const formatCount = (count: number): string => Intl.NumberFormat(i18n.language).format(count);
	const pendingCount = totalCount - completedCount - failedCount;
	const hasAnyProgress = completedCount > 0 || failedCount > 0;

	if (!hasAnyProgress) {
		const description =
			pendingCount > 0 ? t('operate.shared.batchItemsCount.notStarted') : t('operate.shared.batchItemsCount.noItems');

		return (
			<Tooltip>
				<TooltipTrigger asChild>
					<span
						role="status"
						aria-label={description}
						tabIndex={0}
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
			label: t('operate.shared.batchItemsCount.successful', {
				count: completedCount,
				formattedCount: formatCount(completedCount),
			}),
			Icon: CircleCheck,
			className: 'text-success-foreground-subtle',
		},
		{
			key: 'failed',
			count: failedCount,
			label: t('operate.shared.batchItemsCount.failed', {
				count: failedCount,
				formattedCount: formatCount(failedCount),
			}),
			Icon: CircleAlert,
			className: 'text-danger-foreground-subtle',
		},
		{
			key: 'pending',
			count: pendingCount,
			label: t('operate.shared.batchItemsCount.pending', {
				count: pendingCount,
				formattedCount: formatCount(pendingCount),
			}),
			Icon: Clock,
			className: 'text-neutral-foreground-subtle',
		},
	];

	return (
		<div className="flex items-center gap-4">
			{statusConfig
				.filter(({count}) => count > 0)
				.map(({key, count, label, Icon, className}) => (
					<Tooltip key={key}>
						<TooltipTrigger asChild>
							<span
								role="status"
								aria-label={label}
								tabIndex={0}
								className="flex min-w-12 cursor-default items-center gap-2"
							>
								<Icon aria-hidden="true" focusable="false" className={cn('h-4 w-4 shrink-0', className)} />
								{formatCompactCount(count)}
							</span>
						</TooltipTrigger>
						<TooltipContent side="bottom">{label}</TooltipContent>
					</Tooltip>
				))}
		</div>
	);
};

export {BatchItemsCount};
