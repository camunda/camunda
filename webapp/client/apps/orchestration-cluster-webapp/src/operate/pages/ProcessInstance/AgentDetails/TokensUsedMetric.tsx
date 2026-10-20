/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {TokenBreakdown, TokenBreakdownColumn, TokenBreakdownContainer} from './agentDetails.styled';
import {getTotalTokens} from './getTotalTokens';
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

	return (
		<MetricCard
			title={t('operate.processInstance.details.agent.tokensUsed')}
			value={totalTokens}
			valueInfo={t('operate.processInstance.details.agent.totalTokensDescription')}
		>
			<LimitIndicator current={totalTokens} limit={maxTokens} />
			<TokenBreakdownContainer>
				<TokenBreakdownColumn>
					<TokenBreakdown $dotColor="var(--cds-interactive)">
						<span>{t('operate.processInstance.details.agent.input')}</span>
						<span>{inputTokens.toLocaleString(i18n.resolvedLanguage)}</span>
					</TokenBreakdown>
					<TokenBreakdown $dotColor="var(--cds-support-warning)">
						<span>{t('operate.processInstance.details.agent.output')}</span>
						<span>{outputTokens.toLocaleString(i18n.resolvedLanguage)}</span>
					</TokenBreakdown>
					{reasoningTokens > 0 && (
						<TokenBreakdown $dotColor="var(--cds-status-gray)">
							<span>{t('operate.processInstance.details.agent.reasoning')}</span>
							<span>{reasoningTokens.toLocaleString(i18n.resolvedLanguage)}</span>
						</TokenBreakdown>
					)}
				</TokenBreakdownColumn>
				{(cacheReadTokens > 0 || cacheCreationTokens > 0) && (
					<TokenBreakdownColumn>
						<TokenBreakdown $dotColor="var(--cds-status-gray)">
							<span>{t('operate.processInstance.details.agent.cacheRead')}</span>
							<span>{cacheReadTokens.toLocaleString(i18n.resolvedLanguage)}</span>
						</TokenBreakdown>
						<TokenBreakdown $dotColor="var(--cds-status-gray)">
							<span>{t('operate.processInstance.details.agent.cacheWrite')}</span>
							<span>{cacheCreationTokens.toLocaleString(i18n.resolvedLanguage)}</span>
						</TokenBreakdown>
					</TokenBreakdownColumn>
				)}
			</TokenBreakdownContainer>
		</MetricCard>
	);
};

export {TokensUsedMetric};
