/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Fragment, useState} from 'react';
import {useInfiniteQuery} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {Button, Skeleton, Text} from '@camunda/design-system';
import type {
	AgentInstanceHistoryItem,
	AgentInstanceStatus,
	AgentTool,
	QuerySortOrder,
} from '@camunda/camunda-api-zod-schemas/8.11';
import {agentInstanceHistoryQuery} from '../agentHistory.queries';
import {ConversationMessage} from './ConversationMessage';
import {ConversationToggles} from './ConversationToggles';
import {ToolResultMessage} from './ToolResultMessage';
import {useRefetchOnStatusChange} from '../useRefetchOnStatusChange';

function groupByLoopIteration(items: AgentInstanceHistoryItem[]) {
	const uniqueItems = [...new Map(items.map((item) => [item.historyItemKey, item])).values()];
	return uniqueItems.reduce(
		(iterations, item) => iterations.set(item.loopIteration, [...(iterations.get(item.loopIteration) ?? []), item]),
		new Map<number, AgentInstanceHistoryItem[]>(),
	);
}

type HistoryItemProps = {
	item: AgentInstanceHistoryItem;
	availableTools: AgentTool[];
};

const HistoryItem: React.FC<HistoryItemProps> = ({item, availableTools}) => {
	if (item.role === 'CONFIGURATION') {
		return null;
	}

	if (item.role === 'TOOL_RESULT') {
		return <ToolResultMessage availableTools={availableTools} toolCalls={item.toolCalls} content={item.content} />;
	}

	return (
		<ConversationMessage actor={item.role} content={item.content} toolCalls={item.toolCalls} metrics={item.metrics} />
	);
};

type ConversationHistoryProps = {
	agentInstanceKey: string;
	agentInstanceStatus: AgentInstanceStatus;
	availableTools: AgentTool[];
	isVisible: boolean;
	selectedElementInstanceKey: string | null;
	agentsElementInstanceKeys: string[];
};

const ConversationHistory: React.FC<ConversationHistoryProps> = ({
	agentInstanceKey,
	agentInstanceStatus,
	availableTools,
	isVisible,
	selectedElementInstanceKey,
	agentsElementInstanceKeys,
}) => {
	const {t} = useTranslation();
	const [sortOrder, setSortOrder] = useState<QuerySortOrder>('DESC');
	const [isScoped, setIsScoped] = useState(true);
	const canBeScoped = agentsElementInstanceKeys.length > 1 && selectedElementInstanceKey !== null;
	const isScopedQuery = canBeScoped && isScoped;

	const {data, isError, refetch, isPlaceholderData, hasNextPage, fetchNextPage, isFetchingNextPage} = useInfiniteQuery({
		...agentInstanceHistoryQuery({
			agentInstanceKey,
			agentInstanceStatus,
			sortOrder,
			elementInstanceKey: isScopedQuery ? selectedElementInstanceKey : null,
		}),
		enabled: isVisible,
	});

	useRefetchOnStatusChange(agentInstanceStatus, isVisible, refetch);

	const errorHint = (
		<Text as="span" variant="body-sm" className="text-center font-medium text-[var(--neutral-foreground-subtle)]">
			{t('operate.processInstance.details.agent.historyLoadError')}
		</Text>
	);
	const toggles = (
		<ConversationToggles
			sortOrder={sortOrder}
			canBeScoped={canBeScoped}
			isScoped={isScoped}
			onToggleSortOrder={() => setSortOrder((previous) => (previous === 'DESC' ? 'ASC' : 'DESC'))}
			onToggleScope={() => setIsScoped((previous) => !previous)}
		/>
	);

	if (data === undefined) {
		return (
			<div className="flex flex-col gap-4">
				{toggles}
				{isError ? (
					errorHint
				) : (
					<div className="space-y-2">
						<Skeleton className="h-4 w-24" />
						<Skeleton className="h-4 w-full" />
						<Skeleton className="h-4 w-4/5" />
					</div>
				)}
			</div>
		);
	}

	const iterations = groupByLoopIteration(data.pages.flatMap((page) => page.items));

	return (
		<div className="flex flex-col gap-4">
			{toggles}
			<div
				className={`flex flex-col gap-4 transition-opacity ${isPlaceholderData ? 'pointer-events-none opacity-50' : ''}`}
			>
				{iterations.size === 0 ? (
					<Text as="span" variant="body-sm" className="text-center font-medium text-[var(--neutral-foreground-subtle)]">
						{isScopedQuery
							? t('operate.processInstance.details.agent.noScopedConversation')
							: t('operate.processInstance.details.agent.noConversation')}
					</Text>
				) : (
					Array.from(iterations, ([iteration, items]) => {
						const iterationMarker = (
							<Text
								as="span"
								variant="label-sm"
								className="inline-flex items-center justify-center gap-4 text-[var(--neutral-foreground-subtle)] before:inline-block before:h-px before:w-[8ch] before:rounded before:bg-[var(--border)] after:inline-block after:h-px after:w-[8ch] after:rounded after:bg-[var(--border)]"
							>
								{t('operate.processInstance.details.agent.iteration', {value: iteration})}
							</Text>
						);

						return (
							<Fragment key={iteration}>
								{sortOrder === 'ASC' && iterationMarker}
								{items.map((item) => (
									<HistoryItem key={item.historyItemKey} item={item} availableTools={availableTools} />
								))}
								{sortOrder === 'DESC' && iterationMarker}
							</Fragment>
						);
					})
				)}
			</div>
			{isError && errorHint}
			{isFetchingNextPage && (
				<div className="space-y-2">
					<Skeleton className="h-4 w-full" />
					<Skeleton className="h-4 w-5/6" />
				</div>
			)}
			{!isFetchingNextPage && !isPlaceholderData && hasNextPage && (
				<Button variant="ghost" size="sm" className="self-center" onClick={() => fetchNextPage()}>
					{t('operate.processInstance.details.agent.showMore')}
				</Button>
			)}
		</div>
	);
};

export {ConversationHistory};
