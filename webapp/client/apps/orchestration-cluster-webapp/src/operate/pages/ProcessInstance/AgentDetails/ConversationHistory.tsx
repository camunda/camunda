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
import {SkeletonText} from '@carbon/react';
import type {
	AgentInstanceHistoryItem,
	AgentInstanceStatus,
	AgentTool,
	QuerySortOrder,
} from '@camunda/camunda-api-zod-schemas/8.11';
import {agentInstanceHistoryQuery} from './agentHistory.queries';
import {ConversationMessage} from './ConversationMessage';
import {ConversationToggles} from './ConversationToggles';
import {ConversationContainer, LoopIterationMarker, Messages, ShowMoreButton, StatusHint} from './conversation.styled';
import {ToolResultMessage} from './ToolResultMessage';
import {useRefetchOnStatusChange} from './useRefetchOnStatusChange';

function groupByLoopIteration(items: AgentInstanceHistoryItem[]) {
	const uniqueItems = [...new Map(items.map((item) => [item.historyItemKey, item])).values()];
	return uniqueItems.reduce(
		(iterations, item) => iterations.set(item.loopIteration, [...(iterations.get(item.loopIteration) ?? []), item]),
		new Map<number, AgentInstanceHistoryItem[]>(),
	);
}

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

	const errorHint = <StatusHint>{t('operate.processInstance.details.agent.historyLoadError')}</StatusHint>;
	const iterations = groupByLoopIteration(data?.pages.flatMap((page) => page.items) ?? []);

	return (
		<ConversationContainer>
			<ConversationToggles
				sortOrder={sortOrder}
				canBeScoped={canBeScoped}
				isScoped={isScoped}
				onToggleSortOrder={() => setSortOrder((previous) => (previous === 'DESC' ? 'ASC' : 'DESC'))}
				onToggleScope={() => setIsScoped((previous) => !previous)}
			/>
			{data === undefined ? (
				isError ? (
					errorHint
				) : (
					<SkeletonText heading paragraph lineCount={3} />
				)
			) : (
				<>
					<Messages data-dimmed={isPlaceholderData}>
						{iterations.size === 0 ? (
							<StatusHint>
								{isScopedQuery
									? t('operate.processInstance.details.agent.noScopedConversation')
									: t('operate.processInstance.details.agent.noConversation')}
							</StatusHint>
						) : (
							Array.from(iterations, ([iteration, items]) => {
								const iterationMarker = (
									<LoopIterationMarker>
										{t('operate.processInstance.details.agent.iteration', {value: iteration})}
									</LoopIterationMarker>
								);

								return (
									<Fragment key={iteration}>
										{sortOrder === 'ASC' && iterationMarker}
										{items.map((item) =>
											item.role === 'CONFIGURATION' ? null : item.role === 'TOOL_RESULT' ? (
												<ToolResultMessage
													key={item.historyItemKey}
													availableTools={availableTools}
													toolCalls={item.toolCalls}
													content={item.content}
												/>
											) : (
												<ConversationMessage
													key={item.historyItemKey}
													actor={item.role}
													content={item.content}
													toolCalls={item.toolCalls}
													metrics={item.metrics}
												/>
											),
										)}
										{sortOrder === 'DESC' && iterationMarker}
									</Fragment>
								);
							})
						)}
					</Messages>
					{isError && errorHint}
					{isFetchingNextPage && <SkeletonText paragraph lineCount={2} />}
					{!isFetchingNextPage && !isPlaceholderData && hasNextPage && (
						<ShowMoreButton kind="ghost" size="sm" onClick={() => fetchNextPage()}>
							{t('operate.processInstance.details.agent.showMore')}
						</ShowMoreButton>
					)}
				</>
			)}
		</ConversationContainer>
	);
};

export {ConversationHistory};
