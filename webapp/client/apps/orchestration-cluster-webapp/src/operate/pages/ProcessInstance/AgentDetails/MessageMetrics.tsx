/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Fragment} from 'react';
import {useTranslation} from 'react-i18next';
import {Tag, Tooltip} from '@carbon/react';
import type {AgentInstanceHistoryItemMetrics} from '@camunda/camunda-api-zod-schemas/8.11';
import {MetricsContainer} from './conversation.styled';
import {getTotalTokens} from './getTotalTokens';

function formatDuration(ms: number): string {
	return ms < 1000 ? `${ms}ms` : `${(ms / 1000).toFixed(2)}s`;
}

const MessageMetrics: React.FC<{metrics: AgentInstanceHistoryItemMetrics | null}> = ({metrics}) => {
	const {t, i18n} = useTranslation();

	if (metrics === null) {
		return null;
	}

	const hasTokenMetrics =
		metrics.inputTokens !== null ||
		metrics.outputTokens !== null ||
		metrics.cacheReadTokenCount !== null ||
		metrics.cacheCreationTokenCount !== null;

	if (!hasTokenMetrics && metrics.durationMs === null) {
		return null;
	}

	const formatToken = (tokens: number | null) => tokens?.toLocaleString(i18n.resolvedLanguage) ?? '---';
	const output = t('operate.processInstance.details.agent.outputTokens', {value: formatToken(metrics.outputTokens)});
	const tooltipLines = [
		t('operate.processInstance.details.agent.inputTokens', {value: formatToken(metrics.inputTokens)}),
		...(metrics.cacheReadTokenCount === null
			? []
			: [
					t('operate.processInstance.details.agent.cacheReadTokens', {value: formatToken(metrics.cacheReadTokenCount)}),
				]),
		...(metrics.cacheCreationTokenCount === null
			? []
			: [
					t('operate.processInstance.details.agent.cacheWriteTokens', {
						value: formatToken(metrics.cacheCreationTokenCount),
					}),
				]),
		metrics.reasoningTokenCount === null
			? output
			: t('operate.processInstance.details.agent.outputTokensWithReasoning', {
					output,
					value: formatToken(metrics.reasoningTokenCount),
				}),
	];

	return (
		<MetricsContainer>
			{hasTokenMetrics && (
				<Tooltip
					description={tooltipLines.map((line, index) => (
						<Fragment key={line}>
							{index > 0 && <br />}
							{line}
						</Fragment>
					))}
					align="bottom"
				>
					<Tag type="gray" size="sm">
						{t('operate.processInstance.details.agent.tokensTag', {
							value: getTotalTokens(metrics).toLocaleString(i18n.resolvedLanguage),
						})}
					</Tag>
				</Tooltip>
			)}
			{metrics.durationMs !== null && (
				<Tag type="gray" size="sm">
					{formatDuration(metrics.durationMs)}
				</Tag>
			)}
		</MetricsContainer>
	);
};

export {MessageMetrics};
