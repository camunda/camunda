/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import type {CSSProperties} from 'react';

type LimitIndicatorProps = {
	current: number;
	limit: number;
};

const LimitIndicator: React.FC<LimitIndicatorProps> = ({current, limit}) => {
	const {t, i18n} = useTranslation();

	if (limit < 0) {
		return null;
	}

	const usage = limit === 0 ? 100 : (current / limit) * 100;
	const clampedUsage = Math.max(0, Math.min(usage, 100));

	return (
		<div className="flex flex-col gap-2">
			<div
				className="h-1 w-full overflow-hidden rounded-sm bg-[var(--neutral-border-subtle)]"
				style={{'--agent-limit-usage': `${clampedUsage}%`} as CSSProperties}
				aria-label={t('operate.processInstance.details.agent.usageLimit')}
				role="meter"
				aria-valuemin={0}
				aria-valuemax={100}
				aria-valuenow={clampedUsage}
				aria-valuetext={t('operate.processInstance.details.agent.usageLimitValue', {
					current: current.toLocaleString(i18n.resolvedLanguage),
					limit: limit.toLocaleString(i18n.resolvedLanguage),
					percent: usage.toFixed(0),
				})}
			>
				<span
					className="block h-full rounded-sm bg-[var(--info-action-default)]"
					style={{width: 'var(--agent-limit-usage)'}}
					aria-hidden
				/>
			</div>
			<span className="text-xs text-[var(--neutral-foreground-subtle)]" aria-hidden="true">
				{t('operate.processInstance.details.agent.ofLimit', {limit: limit.toLocaleString(i18n.resolvedLanguage)})}
			</span>
		</div>
	);
};

export {LimitIndicator};
