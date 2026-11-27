/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {getTotalTokens} from '../getTotalTokens';
import {LimitIndicator} from './LimitIndicator';
import {MetricCard} from './MetricCard';

type TokensUsedMetricProps = {
	inputTokens: number;
	outputTokens: number;
	cacheReadTokens: number;
	cacheCreationTokens: number;
	reasoningTokens: number;
	maxTokens: number;
};

const TokensUsedMetric: React.FC<TokensUsedMetricProps> = ({
	inputTokens,
	outputTokens,
	cacheReadTokens,
	cacheCreationTokens,
	reasoningTokens,
	maxTokens,
}) => {
	const {t, i18n} = useTranslation();
	const totalTokens = getTotalTokens({
		inputTokens,
		outputTokens,
		cacheReadTokenCount: cacheReadTokens,
		cacheCreationTokenCount: cacheCreationTokens,
	});
	const dotBaseClassName = 'before:inline-block before:size-3 before:rounded-full before:content-[""]';

	return (
		<MetricCard
			title={t('operate.processInstance.details.agent.tokensUsed')}
			value={totalTokens}
			valueInfo={t('operate.processInstance.details.agent.totalTokensDescription')}
		>
			<LimitIndicator current={totalTokens} limit={maxTokens} />
			<div className="flex flex-wrap gap-x-8 gap-y-2">
				<div className="flex flex-col gap-2">
					<span
						className={`${dotBaseClassName} inline-flex items-center gap-3 text-xs text-[var(--neutral-foreground-subtle)] before:bg-[var(--chart-blue)]`}
					>
						<span>{t('operate.processInstance.details.agent.input')}</span>
						<span className="text-foreground">{inputTokens.toLocaleString(i18n.resolvedLanguage)}</span>
					</span>
					<span
						className={`${dotBaseClassName} inline-flex items-center gap-3 text-xs text-[var(--neutral-foreground-subtle)] before:bg-[var(--warning-action-default)]`}
					>
						<span>{t('operate.processInstance.details.agent.output')}</span>
						<span className="text-foreground">{outputTokens.toLocaleString(i18n.resolvedLanguage)}</span>
					</span>
					{reasoningTokens > 0 && (
						<span
							className={`${dotBaseClassName} inline-flex items-center gap-3 text-xs text-[var(--neutral-foreground-subtle)] before:bg-[var(--chart-purple)]`}
						>
							<span>{t('operate.processInstance.details.agent.reasoning')}</span>
							<span className="text-foreground">{reasoningTokens.toLocaleString(i18n.resolvedLanguage)}</span>
						</span>
					)}
				</div>
				{(cacheReadTokens > 0 || cacheCreationTokens > 0) && (
					<div className="flex flex-col gap-2">
						<span
							className={`${dotBaseClassName} inline-flex items-center gap-3 text-xs text-[var(--neutral-foreground-subtle)] before:bg-[var(--chart-teal)]`}
						>
							<span>{t('operate.processInstance.details.agent.cacheRead')}</span>
							<span className="text-foreground">{cacheReadTokens.toLocaleString(i18n.resolvedLanguage)}</span>
						</span>
						<span
							className={`${dotBaseClassName} inline-flex items-center gap-3 text-xs text-[var(--neutral-foreground-subtle)] before:bg-[var(--chart-pink)]`}
						>
							<span>{t('operate.processInstance.details.agent.cacheWrite')}</span>
							<span className="text-foreground">{cacheCreationTokens.toLocaleString(i18n.resolvedLanguage)}</span>
						</span>
					</div>
				)}
			</div>
		</MetricCard>
	);
};

export {TokensUsedMetric};
