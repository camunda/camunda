/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useId, useState} from 'react';
import {useTranslation} from 'react-i18next';
import type {TFunction} from 'i18next';
import {Accordion, AccordionItem, Tag} from '@carbon/react';
import {CheckmarkOutline, Chip, CircleDash, MeterAlt, Time, Tools, WarningFilled} from '@carbon/react/icons';
import type {AgentInstance, AgentInstanceStatus} from '@camunda/camunda-api-zod-schemas/8.11';
import {SectionHeading} from '../details.styled';
import {
	AgentHeader,
	AgentDetailsContainer,
	MetricsRow,
	ModelInfo,
	ModelInfoLabel,
	SecondaryText,
} from './agentDetails.styled';
import {AgentSectionTitle} from './AgentSectionTitle';
import {AgentSelector} from './AgentSelector';
import {AvailableTools} from './AvailableTools';
import {getTotalTokens} from './getTotalTokens';
import {ModelCallsMetric} from './ModelCallsMetric';
import {TokensUsedMetric} from './TokensUsedMetric';
import {ToolsCalledMetric} from './ToolsCalledMetric';

const STATUS_LABEL_KEYS: Record<AgentInstanceStatus, string> = {
	UNKNOWN: 'operate.processInstance.details.agent.statusUnknown',
	INITIALIZING: 'operate.processInstance.details.agent.statusInitializing',
	THINKING: 'operate.processInstance.details.agent.statusThinking',
	TOOL_CALLING: 'operate.processInstance.details.agent.statusToolCalling',
	TOOL_DISCOVERY: 'operate.processInstance.details.agent.statusToolDiscovery',
	IDLE: 'operate.processInstance.details.agent.statusIdle',
	COMPLETED: 'operate.processInstance.details.agent.statusCompleted',
};

function getStatusLabel(t: TFunction, status: AgentInstanceStatus) {
	const key = STATUS_LABEL_KEYS[status];
	return key === undefined ? status : t(key);
}

function StatusIcon({status}: {status: AgentInstanceStatus}) {
	switch (status) {
		case 'INITIALIZING':
		case 'THINKING':
		case 'TOOL_CALLING':
		case 'TOOL_DISCOVERY':
			return <Time size={16} />;
		case 'IDLE':
			return <CircleDash size={16} />;
		case 'COMPLETED':
			return <CheckmarkOutline size={16} />;
		default:
			return <WarningFilled size={16} />;
	}
}

type AgentDetailsProps = {
	agentInstances: AgentInstance[];
	totalAgentsCount: number;
	hasMoreTotalItems: boolean;
	isError: boolean;
};

const AgentDetails: React.FC<AgentDetailsProps> = ({agentInstances, totalAgentsCount, hasMoreTotalItems, isError}) => {
	const {t, i18n} = useTranslation();
	const headingId = useId();
	const [selectedAgentInstanceKey, setSelectedAgentInstanceKey] = useState<string | null>(null);

	const agentInstance =
		agentInstances.find((agent) => agent.agentInstanceKey === selectedAgentInstanceKey) ?? agentInstances[0] ?? null;
	const heading = t('operate.processInstance.details.agent.heading');

	if (isError || !agentInstance) {
		return (
			<AgentDetailsContainer aria-labelledby={headingId}>
				<SectionHeading id={headingId}>{heading}</SectionHeading>
				<SecondaryText>{t('operate.processInstance.details.agent.loadError')}</SecondaryText>
			</AgentDetailsContainer>
		);
	}

	const selectableAgentInstances = agentInstances.map((agent) => ({
		agentInstanceKey: agent.agentInstanceKey,
		label: `${agent.agentInstanceKey} - ${getStatusLabel(t, agent.status)}`,
	}));
	const {metrics, limits, definition} = agentInstance;
	const totalTokens = getTotalTokens(metrics);
	const remainingAgentsCount =
		agentInstances.length < totalAgentsCount
			? `${totalAgentsCount - agentInstances.length}${hasMoreTotalItems ? '+' : ''}`
			: undefined;

	return (
		<AgentDetailsContainer aria-labelledby={headingId} data-testid="agent-details">
			<AgentHeader>
				<SectionHeading id={headingId}>{heading}</SectionHeading>
				{selectableAgentInstances.length > 1 ? (
					<AgentSelector
						agents={selectableAgentInstances}
						remainingAgentsCount={remainingAgentsCount}
						selectedAgentInstanceKey={agentInstance.agentInstanceKey}
						onChange={setSelectedAgentInstanceKey}
					/>
				) : (
					<SecondaryText>{agentInstance.agentInstanceKey}</SecondaryText>
				)}
			</AgentHeader>
			<Accordion align="start">
				<AccordionItem
					data-testid="agent-status-section"
					open
					title={
						<AgentSectionTitle icon={<StatusIcon status={agentInstance.status} />}>
							{t('operate.processInstance.details.agent.statusTitle', {
								status: getStatusLabel(t, agentInstance.status),
							})}
						</AgentSectionTitle>
					}
				/>
				<AccordionItem
					data-testid="agent-usage-section"
					aria-label={t('operate.processInstance.details.agent.usage')}
					title={
						<AgentSectionTitle icon={<MeterAlt size={16} />}>
							{t('operate.processInstance.details.agent.usage')}
							<Tag type="gray" size="sm">
								{t('operate.processInstance.details.agent.modelCallsTag', {
									value: metrics.modelCalls.toLocaleString(i18n.resolvedLanguage),
								})}
							</Tag>
							<Tag type="gray" size="sm">
								{t('operate.processInstance.details.agent.tokensTag', {
									value: totalTokens.toLocaleString(i18n.resolvedLanguage),
								})}
							</Tag>
						</AgentSectionTitle>
					}
				>
					<MetricsRow>
						<ModelCallsMetric modelCalls={metrics.modelCalls} maxModelCalls={limits.maxModelCalls} />
						<TokensUsedMetric
							inputTokens={metrics.inputTokens}
							outputTokens={metrics.outputTokens}
							cacheReadTokens={metrics.cacheReadTokenCount}
							cacheCreationTokens={metrics.cacheCreationTokenCount}
							reasoningTokens={metrics.reasoningTokenCount}
							maxTokens={limits.maxTokens}
						/>
						<ToolsCalledMetric toolCalls={metrics.toolCalls} maxToolCalls={limits.maxToolCalls} />
					</MetricsRow>
				</AccordionItem>
				<AccordionItem
					data-testid="agent-available-tools-section"
					title={
						<AgentSectionTitle icon={<Tools size={16} />}>
							{t('operate.processInstance.details.agent.availableTools')}
						</AgentSectionTitle>
					}
				>
					<AvailableTools tools={agentInstance.tools} />
				</AccordionItem>
				<AccordionItem
					data-testid="agent-model-section"
					title={
						<AgentSectionTitle icon={<Chip size={16} />}>
							{t('operate.processInstance.details.agent.modelSection')}
						</AgentSectionTitle>
					}
				>
					<ModelInfo>
						<ModelInfoLabel>{t('operate.processInstance.details.agent.provider')}</ModelInfoLabel> {definition.provider}
					</ModelInfo>
					<ModelInfo>
						<ModelInfoLabel>{t('operate.processInstance.details.agent.model')}</ModelInfoLabel> {definition.model}
					</ModelInfo>
					<ModelInfo>
						<ModelInfoLabel>{t('operate.processInstance.details.agent.agentDefinitionKey')}</ModelInfoLabel>{' '}
						{agentInstance.agentDefinitionKey}
					</ModelInfo>
				</AccordionItem>
			</Accordion>
		</AgentDetailsContainer>
	);
};

export {AgentDetails};
