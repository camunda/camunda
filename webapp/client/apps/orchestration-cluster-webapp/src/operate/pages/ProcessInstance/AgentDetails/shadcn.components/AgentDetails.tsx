/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useId, useRef, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {
	Accordion,
	AccordionContent,
	AccordionItem,
	AccordionTrigger,
	Badge,
	Heading,
	Text,
} from '@camunda/design-system';
import {
	Bot,
	CheckCircle2,
	CircleDashed,
	Clock,
	FileText,
	Gauge,
	MessageCircle,
	TriangleAlert,
	Wrench,
} from '@camunda/design-system/icons';
import type {AgentInstance, AgentInstanceStatus} from '@camunda/camunda-api-zod-schemas/8.11';
import {getTotalTokens} from '../getTotalTokens';
import {AgentSectionTitle} from './AgentSectionTitle';
import {AgentSelector} from './AgentSelector';
import {AvailableTools} from './AvailableTools';
import {ConversationHistory} from './ConversationHistory';
import {ConversationMessage} from './ConversationMessage';
import {LatestAgentMessage} from './LatestAgentMessage';
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

function getStatusLabel(t: (key: string) => string, status: AgentInstanceStatus) {
	const key = STATUS_LABEL_KEYS[status];
	return key === undefined ? status : t(key);
}

function StatusIcon({status}: {status: AgentInstanceStatus}) {
	switch (status) {
		case 'INITIALIZING':
		case 'THINKING':
		case 'TOOL_CALLING':
		case 'TOOL_DISCOVERY':
			return <Clock aria-hidden />;
		case 'IDLE':
			return <CircleDashed aria-hidden />;
		case 'COMPLETED':
			return <CheckCircle2 aria-hidden />;
		default:
			return <TriangleAlert aria-hidden />;
	}
}

type SectionKey = 'status' | 'usage' | 'conversation' | 'systemPrompt' | 'tools' | 'model';

type AgentSectionProps = {
	value: SectionKey;
	triggerRef?: React.Ref<HTMLButtonElement>;
	title: React.ReactNode;
	buttonLabel?: string;
	children: React.ReactNode;
};

const AgentSection: React.FC<AgentSectionProps> = ({value, triggerRef, title, buttonLabel, children}) => (
	<AccordionItem value={value}>
		<AccordionTrigger ref={triggerRef} aria-label={buttonLabel}>
			{title}
		</AccordionTrigger>
		<AccordionContent forceMount>{children}</AccordionContent>
	</AccordionItem>
);

type AgentDetailsProps = {
	selectedElementInstanceKey: string | null;
	agentInstances: AgentInstance[];
	totalAgentsCount: number;
	hasMoreTotalItems: boolean;
	isError: boolean;
};

const AgentDetails: React.FC<AgentDetailsProps> = ({
	selectedElementInstanceKey,
	agentInstances,
	totalAgentsCount,
	hasMoreTotalItems,
	isError,
}) => {
	const {t, i18n} = useTranslation();
	const headingId = useId();
	const [selectedAgentInstanceKey, setSelectedAgentInstanceKey] = useState<string | null>(null);
	const [openSections, setOpenSections] = useState<string[]>(['status']);
	const conversationTriggerRef = useRef<HTMLButtonElement>(null);

	const agentInstance =
		agentInstances.find((agent) => agent.agentInstanceKey === selectedAgentInstanceKey) ?? agentInstances[0] ?? null;
	const heading = t('operate.processInstance.details.agent.heading');

	if (isError || agentInstance === null) {
		return (
			<article aria-labelledby={headingId} className="flex flex-col gap-3 pt-2">
				<Heading as="h2" variant="heading-sm" id={headingId}>
					{heading}
				</Heading>
				<Text as="span" variant="body-sm" className="text-[var(--neutral-foreground-subtle)]">
					{t('operate.processInstance.details.agent.loadError')}
				</Text>
			</article>
		);
	}

	const selectableAgentInstances = agentInstances.map((agent) => ({
		agentInstanceKey: agent.agentInstanceKey,
		label: `${agent.agentInstanceKey} - ${getStatusLabel(t, agent.status)}`,
	}));
	const {metrics, limits, definition} = agentInstance;
	const totalTokens = getTotalTokens(metrics);
	const conversationHistoryTitle = t('operate.processInstance.details.agent.conversationHistory');
	const remainingAgentsCount =
		agentInstances.length < totalAgentsCount
			? `${totalAgentsCount - agentInstances.length}${hasMoreTotalItems ? '+' : ''}`
			: undefined;
	return (
		<article
			aria-labelledby={headingId}
			className="flex flex-col gap-3 pt-2"
			onKeyDown={(event) => {
				if (event.key === 'Escape' && event.target === conversationTriggerRef.current) {
					setOpenSections((current) => current.filter((section) => section !== 'conversation'));
				}
			}}
		>
			<div className="flex items-center gap-3">
				<Heading as="h2" variant="heading-sm" id={headingId}>
					{heading}
				</Heading>
				{selectableAgentInstances.length > 1 ? (
					<AgentSelector
						agents={selectableAgentInstances}
						remainingAgentsCount={remainingAgentsCount}
						selectedAgentInstanceKey={agentInstance.agentInstanceKey}
						onChange={setSelectedAgentInstanceKey}
					/>
				) : (
					<Text as="span" variant="body-sm" className="text-[var(--neutral-foreground-subtle)]">
						{agentInstance.agentInstanceKey}
					</Text>
				)}
			</div>
			<Accordion value={openSections} onValueChange={setOpenSections}>
				<AgentSection
					value="status"
					title={
						<AgentSectionTitle icon={<StatusIcon status={agentInstance.status} />}>
							{t('operate.processInstance.details.agent.statusTitle', {
								status: getStatusLabel(t, agentInstance.status),
							})}
						</AgentSectionTitle>
					}
				>
					<LatestAgentMessage
						agentInstanceKey={agentInstance.agentInstanceKey}
						agentInstanceStatus={agentInstance.status}
					/>
				</AgentSection>
				<AgentSection
					value="usage"
					buttonLabel={t('operate.processInstance.details.agent.usage')}
					title={
						<AgentSectionTitle icon={<Gauge aria-hidden />}>
							{t('operate.processInstance.details.agent.usage')}
							<Badge variant="neutral">
								{t('operate.processInstance.details.agent.modelCallsTag', {
									value: metrics.modelCalls.toLocaleString(i18n.resolvedLanguage),
								})}
							</Badge>
							<Badge variant="neutral">
								{t('operate.processInstance.details.agent.tokensTag', {
									value: totalTokens.toLocaleString(i18n.resolvedLanguage),
								})}
							</Badge>
						</AgentSectionTitle>
					}
				>
					<div className="flex flex-wrap gap-5">
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
					</div>
				</AgentSection>
				<AgentSection
					value="conversation"
					triggerRef={conversationTriggerRef}
					title={<AgentSectionTitle icon={<MessageCircle aria-hidden />}>{conversationHistoryTitle}</AgentSectionTitle>}
				>
					<ConversationHistory
						agentInstanceKey={agentInstance.agentInstanceKey}
						agentInstanceStatus={agentInstance.status}
						availableTools={agentInstance.tools}
						isVisible={openSections.includes('conversation')}
						selectedElementInstanceKey={selectedElementInstanceKey}
						agentsElementInstanceKeys={agentInstance.elementInstanceKeys}
					/>
				</AgentSection>
				<AgentSection
					value="systemPrompt"
					title={
						<AgentSectionTitle icon={<FileText aria-hidden />}>
							{t('operate.processInstance.details.agent.systemPrompt')}
						</AgentSectionTitle>
					}
				>
					<ConversationMessage actor="SYSTEM" content={definition.systemPrompt} />
				</AgentSection>
				<AgentSection
					value="tools"
					title={
						<AgentSectionTitle icon={<Wrench aria-hidden />}>
							{t('operate.processInstance.details.agent.availableTools')}
						</AgentSectionTitle>
					}
				>
					<AvailableTools tools={agentInstance.tools} />
				</AgentSection>
				<AgentSection
					value="model"
					title={
						<AgentSectionTitle icon={<Bot aria-hidden />}>
							{t('operate.processInstance.details.agent.modelSection')}
						</AgentSectionTitle>
					}
				>
					<div className="text-sm text-[var(--neutral-foreground-subtle)]">
						<p>
							<strong className="text-foreground">{t('operate.processInstance.details.agent.provider')}</strong>{' '}
							{definition.provider}
						</p>
						<p>
							<strong className="text-foreground">{t('operate.processInstance.details.agent.model')}</strong>{' '}
							{definition.model}
						</p>
						<p>
							<strong className="text-foreground">
								{t('operate.processInstance.details.agent.agentDefinitionKey')}
							</strong>{' '}
							{agentInstance.agentDefinitionKey}
						</p>
					</div>
				</AgentSection>
			</Accordion>
		</article>
	);
};

export {AgentDetails};
