/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useMemo, useRef} from 'react';
import {useTranslation} from 'react-i18next';
import {Button, EmptyState, Skeleton, TreeView, type TreeNodeDef} from '@camunda/design-system';
import {Search} from '@camunda/design-system/icons';
import type {BusinessObjects} from 'bpmn-js/lib/NavigatedViewer';
import type {ElementInstance} from '@camunda/camunda-api-zod-schemas/8.11';
import {ForbiddenError} from '#/shared/errors';
import {cn} from '#/shared/cn';
import {ErrorMessage} from '#/operate/shared/ErrorMessage/shadcn.components/ErrorMessage';
import {HistoryPagingSentinel} from './HistoryPagingSentinel';
import {HistoryRowLabel} from './HistoryRowLabel';
import {useInstanceHistory} from '../useInstanceHistory';
import {useProcessInstanceElementSelection} from '../useProcessInstanceElementSelection';
import {buildElementSearchRequest, useElementSearchResults} from '../useElementSearchResults';
import type {ElementStatusFilter} from '../processInstanceSearch';

const ROW_HEIGHT = 32;
const PREVIOUS_SENTINEL_ID = 'search-previous-sentinel';
const NEXT_SENTINEL_ID = 'search-next-sentinel';
const RESULT_ID_PREFIX = 'search-result-';

function countRowsAbove(items: ElementInstance[] | undefined, elementInstanceKey: string | undefined) {
	return items?.findIndex((item) => item.elementInstanceKey === elementInstanceKey) ?? -1;
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
	const isPaging = query.isFetchingNextPage || query.isFetchingPreviousPage || query.isPlaceholderData;
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
		return (
			<div className="flex flex-col items-start gap-2 p-2">
				<ErrorMessage message={t('operate.processInstance.history.search.error')} />
				<Button variant="secondary" size="sm" onClick={() => void query.refetch()}>
					{t('errorGenericErrorPageButtonLabel')}
				</Button>
			</div>
		);
	}

	if (query.isPending) {
		return <Skeleton className="m-2 h-6" />;
	}

	const items = query.data.items;
	const count = (
		<p aria-live="polite" className="m-0 px-3 py-2 text-xs text-muted-foreground">
			{t('operate.processInstance.history.search.matchingElements', {count: query.data.totalCount})}
		</p>
	);

	async function fetchPreviousPage() {
		const previousFirstKey = items[0]?.elementInstanceKey;
		const result = await query.fetchPreviousPage();
		const rowsAdded = countRowsAbove(result.data?.items, previousFirstKey);
		if (!result.isFetchPreviousPageError && rowsAdded > 0) {
			scrollRef.current?.scrollBy({top: rowsAdded * ROW_HEIGHT});
		}
	}

	async function fetchNextPage() {
		const previousItems = items;
		const result = await query.fetchNextPage();
		const rowsRemoved = countRowsAbove(previousItems, result.data?.items[0]?.elementInstanceKey);
		if (!result.isFetchNextPageError && rowsRemoved > 0) {
			scrollRef.current?.scrollBy({top: -rowsRemoved * ROW_HEIGHT});
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

	const itemsByNodeId = new Map(items.map((item) => [`${RESULT_ID_PREFIX}${item.elementInstanceKey}`, item]));
	const selected = new Set(
		items
			.filter((item) =>
				selection.isSelected(item.elementId, item.elementInstanceKey, item.type === 'MULTI_INSTANCE_BODY'),
			)
			.map((item) => `${RESULT_ID_PREFIX}${item.elementInstanceKey}`),
	);

	const revision = `${items[0]?.elementInstanceKey}:${items.length}`;
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
			id: `${RESULT_ID_PREFIX}${item.elementInstanceKey}`,
			label: <HistoryRowLabel item={item} businessObject={businessObjects?.[item.elementId]} />,
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
