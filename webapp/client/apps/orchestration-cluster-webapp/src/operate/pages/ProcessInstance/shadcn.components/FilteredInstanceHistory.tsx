/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useLayoutEffect, useMemo, useRef} from 'react';
import {useTranslation} from 'react-i18next';
import {EmptyState, Skeleton, TreeView, type TreeNodeDef} from '@camunda/design-system';
import {Search} from '@camunda/design-system/icons';
import type {BusinessObjects} from 'bpmn-js/lib/NavigatedViewer';
import type {ElementInstance} from '@camunda/camunda-api-zod-schemas/8.11';
import {ForbiddenError} from '#/shared/errors';
import {cn} from '#/shared/cn';
import {DataLoadErrorAlert} from './DataLoadErrorAlert';
import {historyRowOffset} from './historyRowOffset';
import {HistoryPagingSentinel} from './HistoryPagingSentinel';
import {HISTORY_TREE_CLASS_NAME, HistoryRowLabel} from './HistoryRowLabel';
import {useInstanceHistory} from '../useInstanceHistory';
import {useProcessInstanceElementSelection} from '../useProcessInstanceElementSelection';
import {buildElementSearchRequest, useElementSearchResults} from '../useElementSearchResults';
import type {ElementStatusFilter} from '../processInstanceSearch';

const PREVIOUS_SENTINEL_ID = 'search-previous-sentinel';
const NEXT_SENTINEL_ID = 'search-next-sentinel';
const RESULT_ID_PREFIX = 'search-result-';

function resultNodeId(item: ElementInstance) {
	return `${RESULT_ID_PREFIX}${item.elementInstanceKey}`;
}

const EMPTY_STATE_KEYS = {
	noMatches: {
		heading: 'operate.processInstance.history.search.noMatches.heading',
		description: 'operate.processInstance.history.search.noMatches.description',
	},
	noActive: {
		heading: 'operate.processInstance.history.search.noActive.heading',
		description: 'operate.processInstance.history.search.noActive.description',
	},
	noIncidents: {
		heading: 'operate.processInstance.history.search.noIncidents.heading',
		description: 'operate.processInstance.history.search.noIncidents.description',
	},
} as const;

function emptyStateKeys(searchText: string, statusFilter?: ElementStatusFilter) {
	if (searchText !== '') {
		return EMPTY_STATE_KEYS.noMatches;
	}
	return statusFilter === 'active' ? EMPTY_STATE_KEYS.noActive : EMPTY_STATE_KEYS.noIncidents;
}

type Props = {
	processInstanceKey: string;
	searchText: string;
	statusFilter?: ElementStatusFilter;
	businessObjects?: BusinessObjects;
};

const FilteredInstanceHistory: React.FC<Props> = ({processInstanceKey, searchText, statusFilter, businessObjects}) => {
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
	const anchorRef = useRef<{id: string; offset: number; firstKey?: string; length: number} | null>(null);
	const loadedItems = query.data?.items;
	const isPaging = query.isFetchingNextPage || query.isFetchingPreviousPage || query.isPlaceholderData;
	const forbiddenError = query.error instanceof ForbiddenError ? query.error : undefined;

	useEffect(() => {
		if (forbiddenError) {
			handleForbidden(forbiddenError);
		}
	}, [forbiddenError, handleForbidden]);

	useLayoutEffect(() => {
		const anchor = anchorRef.current;
		if (!anchor || !loadedItems) {
			return;
		}
		if (loadedItems[0]?.elementInstanceKey === anchor.firstKey && loadedItems.length === anchor.length) {
			return;
		}
		anchorRef.current = null;
		const offset = historyRowOffset(scrollRef.current, anchor.id);
		if (offset !== undefined && offset !== anchor.offset) {
			scrollRef.current?.scrollBy({top: offset - anchor.offset});
		}
	});

	if (forbiddenError) {
		return null;
	}

	if (query.isError) {
		return (
			<DataLoadErrorAlert
				className="p-2"
				title={t('operate.processInstance.history.search.error')}
				onRetry={() => void query.refetch()}
			/>
		);
	}

	if (query.isPending) {
		return <Skeleton className="m-2 h-6" />;
	}

	const items = query.data.items;
	const count = (
		<p aria-live="polite" className="sr-only">
			{t('operate.processInstance.history.search.matchingElements', {count: query.data.totalCount})}
		</p>
	);

	function anchorTo(item: ElementInstance | undefined) {
		const id = item ? resultNodeId(item) : undefined;
		const offset = id ? historyRowOffset(scrollRef.current, id) : undefined;
		anchorRef.current =
			id && offset !== undefined ? {id, offset, firstKey: items[0]?.elementInstanceKey, length: items.length} : null;
	}

	async function fetchPreviousPage() {
		anchorTo(items[0]);
		const result = await query.fetchPreviousPage();
		if (result.isFetchPreviousPageError) {
			anchorRef.current = null;
		}
	}

	async function fetchNextPage() {
		anchorTo(items[items.length - 1]);
		const result = await query.fetchNextPage();
		if (result.isFetchNextPageError) {
			anchorRef.current = null;
		}
	}

	if (items.length === 0) {
		const emptyKeys = emptyStateKeys(searchText, statusFilter);
		return (
			<>
				{count}
				<div className={cn('flex flex-1 items-center justify-center', query.isPlaceholderData && 'opacity-50')}>
					<EmptyState
						size="sm"
						icon={<Search aria-hidden="true" />}
						heading={t(emptyKeys.heading)}
						description={t(emptyKeys.description)}
					/>
				</div>
			</>
		);
	}

	const itemsByNodeId = new Map(items.map((item) => [resultNodeId(item), item]));
	const selected = new Set(
		items
			.filter((item) =>
				selection.isSelected(item.elementId, item.elementInstanceKey, item.type === 'MULTI_INSTANCE_BODY'),
			)
			.map(resultNodeId),
	);

	const revision = `${items[0]?.elementInstanceKey}:${items.length}:${isPaging}`;
	const data: TreeNodeDef[] = [
		...(query.hasPreviousPage
			? [
					{
						id: PREVIOUS_SENTINEL_ID,
						disabled: true,
						label: (
							<HistoryPagingSentinel
								scrollRef={scrollRef}
								revision={revision}
								onReach={() => {
									if (!isPaging) {
										void fetchPreviousPage();
									}
								}}
							/>
						),
					},
				]
			: []),
		...items.map((item) => ({
			id: resultNodeId(item),
			label: (
				<HistoryRowLabel rowId={resultNodeId(item)} item={item} businessObject={businessObjects?.[item.elementId]} />
			),
		})),
		...(query.hasNextPage
			? [
					{
						id: NEXT_SENTINEL_ID,
						disabled: true,
						label: (
							<HistoryPagingSentinel
								scrollRef={scrollRef}
								revision={revision}
								onReach={() => {
									if (!isPaging) {
										void fetchNextPage();
									}
								}}
							/>
						),
					},
				]
			: []),
	];

	return (
		<>
			{count}
			<div
				ref={scrollRef}
				inert={query.isPlaceholderData || undefined}
				className={cn('min-h-0 flex-1 overflow-y-auto [overflow-anchor:none]', query.isPlaceholderData && 'opacity-50')}
			>
				<TreeView
					label={t('operate.processInstance.history.search.results')}
					hideLabel
					size="sm"
					className={HISTORY_TREE_CLASS_NAME}
					data={data}
					selected={selected}
					onSelect={(_, node) => {
						const item = itemsByNodeId.get(node.id);
						if (item) {
							void selection.selectElementInstance(item);
						}
					}}
				/>
			</div>
		</>
	);
};

export {FilteredInstanceHistory};
