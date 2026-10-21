/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useMemo, useRef} from 'react';
import {useTranslation} from 'react-i18next';
import {FeatureFlags, SkeletonText, TreeView} from '@carbon/react';
import {Search} from '@carbon/react/icons';
import type {BusinessObjects} from 'bpmn-js/lib/NavigatedViewer';
import type {ElementInstance} from '@camunda/camunda-api-zod-schemas/8.11';
import {ForbiddenError} from '#/shared/errors';
import {EmptyState} from '#/operate/components/EmptyState/EmptyState';
import {ErrorMessage} from '#/operate/shared/ErrorMessage/ErrorMessage';
import {InfiniteScroller} from '#/operate/shared/InfiniteScroller/InfiniteScroller';
import {StateIcon} from '#/operate/shared/StateIcon/StateIcon';
import {formatTimestamp} from '#/operate/shared/utils/formatTimestamp';
import {ElementInstanceIcon} from './ElementInstanceIcon';
import {useInstanceHistory} from './useInstanceHistory';
import {useProcessInstanceElementSelection} from './useProcessInstanceElementSelection';
import {buildElementSearchRequest, useElementSearchResults} from './useElementSearchResults';
import type {ElementStatusFilter} from './processInstanceSearch';
import {
	DimmableResults,
	EmptyResults,
	HistoryBar,
	HistoryMetadata,
	HistoryName,
	HistoryState,
	HistoryTimestamp,
	HistoryTreeNode,
	ResultsCount,
	SearchResultIcon,
	SearchResultsScroll,
} from './instanceHistory.styled';

const ROW_HEIGHT = 32;

function SearchResultRow({item, businessObjects}: {item: ElementInstance; businessObjects?: BusinessObjects}) {
	const selection = useProcessInstanceElementSelection();
	const label = item.elementName ?? item.elementId;
	return (
		<HistoryTreeNode
			id={`search-result-${item.elementInstanceKey}`}
			value={item.elementInstanceKey}
			aria-label={label}
			renderIcon={() => (
				<SearchResultIcon>
					<ElementInstanceIcon businessObject={businessObjects?.[item.elementId]} root={item.type === 'PROCESS'} />
				</SearchResultIcon>
			)}
			onSelect={() => void selection.selectElementInstance(item)}
			label={
				<HistoryBar>
					<HistoryState>
						<StateIcon state={item.hasIncident ? 'INCIDENT' : item.state} size={16} />
					</HistoryState>
					<HistoryName>{label}</HistoryName>
					{item.endDate && (
						<HistoryMetadata>
							<HistoryTimestamp>{formatTimestamp(item.endDate)}</HistoryTimestamp>
						</HistoryMetadata>
					)}
				</HistoryBar>
			}
		/>
	);
}

function FilteredInstanceHistory({
	processInstanceKey,
	searchText,
	statusFilter,
	businessObjects,
}: {
	processInstanceKey: string;
	searchText: string;
	statusFilter?: ElementStatusFilter;
	businessObjects?: BusinessObjects;
}) {
	const {t} = useTranslation();
	const history = useInstanceHistory();
	const {handleForbidden} = history;
	const selection = useProcessInstanceElementSelection();
	const scrollRef = useRef<HTMLDivElement>(null);
	const request = useMemo(
		() => buildElementSearchRequest({processInstanceKey, searchText, statusFilter, sortOrder: history.sortOrder}),
		[processInstanceKey, searchText, statusFilter, history.sortOrder],
	);
	const query = useElementSearchResults(request);
	const isPaging = query.isFetchingNextPage || query.isFetchingPreviousPage || query.isPlaceholderData;

	// Pages can overlap when live data shifts rows, and duplicates are dropped, so compensate by the rows that
	// were actually added or removed above the previous first row rather than by a full page.
	async function fetchPreviousPage(scrollDown: (distance: number) => void) {
		const firstKey = query.data?.items[0]?.elementInstanceKey;
		const result = await query.fetchPreviousPage();
		const addedAbove = result.data?.items.findIndex((item) => item.elementInstanceKey === firstKey) ?? -1;
		if (!result.isFetchPreviousPageError && addedAbove > 0) {
			scrollDown(addedAbove * ROW_HEIGHT);
		}
	}

	async function fetchNextPage(scrollUp: (distance: number) => void) {
		const keys = query.data?.items.map((item) => item.elementInstanceKey) ?? [];
		const result = await query.fetchNextPage();
		const firstKey = result.data?.items[0]?.elementInstanceKey;
		const removedAbove = firstKey === undefined ? -1 : keys.indexOf(firstKey);
		if (!result.isFetchNextPageError && removedAbove > 0) {
			scrollUp(removedAbove * ROW_HEIGHT);
		}
	}
	const forbiddenError = query.error instanceof ForbiddenError ? query.error : undefined;

	useEffect(() => {
		if (forbiddenError) {
			handleForbidden(forbiddenError);
		}
	}, [forbiddenError, handleForbidden]);

	if (forbiddenError) {
		return null;
	}

	if (query.isError) {
		return <ErrorMessage message={t('operate.processInstance.history.search.error')} />;
	}

	if (query.isPending) {
		return <SkeletonText />;
	}

	if (query.data.items.length === 0) {
		const emptyKey = emptyStateKey(searchText, statusFilter);
		return (
			<DimmableResults inert={query.isPlaceholderData || undefined} $dimmed={query.isPlaceholderData}>
				<EmptyResults>
					<EmptyState
						heading={t(`operate.processInstance.history.search.${emptyKey}.heading`)}
						description={t(`operate.processInstance.history.search.${emptyKey}.description`)}
						icon={<Search size={32} />}
					/>
				</EmptyResults>
			</DimmableResults>
		);
	}

	const selected = query.data.items
		.filter((item) =>
			selection.isSelected(item.elementId, item.elementInstanceKey, item.type === 'MULTI_INSTANCE_BODY'),
		)
		.map((item) => `search-result-${item.elementInstanceKey}`);

	return (
		<>
			<ResultsCount aria-live="polite">
				{t('operate.processInstance.history.search.matchingElements', {count: query.data.totalCount})}
			</ResultsCount>
			<DimmableResults inert={query.isPlaceholderData || undefined} $dimmed={query.isPlaceholderData}>
				<SearchResultsScroll ref={scrollRef}>
					<FeatureFlags enableTreeviewControllable>
						<TreeView
							label={t('operate.processInstance.history.search.results')}
							hideLabel
							selected={selected}
							active={selected[0] ?? ''}
						>
							<InfiniteScroller
								scrollableContainerRef={scrollRef}
								onVerticalScrollStartReach={(scrollDown) => {
									if (query.hasPreviousPage && !isPaging) {
										void fetchPreviousPage(scrollDown);
									}
								}}
								onVerticalScrollEndReach={(scrollUp) => {
									if (query.hasNextPage && !isPaging) {
										void fetchNextPage(scrollUp);
									}
								}}
							>
								<ul>
									{query.data.items.map((item) => (
										<SearchResultRow key={item.elementInstanceKey} item={item} businessObjects={businessObjects} />
									))}
								</ul>
							</InfiniteScroller>
						</TreeView>
					</FeatureFlags>
				</SearchResultsScroll>
			</DimmableResults>
		</>
	);
}

function emptyStateKey(searchText: string, statusFilter?: ElementStatusFilter) {
	if (searchText !== '') {
		return 'noMatches';
	}
	return statusFilter === 'active' ? 'noActive' : 'noIncidents';
}

export {FilteredInstanceHistory};
