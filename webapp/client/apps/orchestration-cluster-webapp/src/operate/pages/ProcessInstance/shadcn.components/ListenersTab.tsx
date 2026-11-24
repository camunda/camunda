/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {useInfiniteQuery} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {
	Label,
	Select,
	SelectContent,
	SelectItem,
	SelectTrigger,
	SelectValue,
	Skeleton,
	StatusIcon,
} from '@camunda/design-system';
import {EmptyMessage} from '#/operate/shared/EmptyMessage/shadcn.components/EmptyMessage';
import {CenteredContent} from '#/operate/shared/CenteredContent/shadcn.components/CenteredContent';
import {TruncatedText} from '#/operate/shared/TruncatedText/shadcn.components/TruncatedText';
import {StructuredList} from '#/operate/shared/StructuredList/shadcn.components/StructuredList';
import {spaceAndCapitalize} from '#/operate/shared/utils/spaceAndCapitalize';
import {formatTimestamp} from '#/operate/shared/utils/formatTimestamp';
import {listenersQuery, type ListenerKind} from '../listeners.queries';
import {useProcessInstanceElementSelection} from '../useProcessInstanceElementSelection';
import {useProcessInstancePage} from '../useProcessInstancePage';
import {DataLoadErrorAlert} from './DataLoadErrorAlert';

type FilterOption = 'all' | 'execution' | 'task';

const FILTER_OPTIONS: FilterOption[] = ['all', 'execution', 'task'];
const SKELETON_ROWS = [0, 1, 2, 3];
const LISTENER_KIND: Record<Exclude<FilterOption, 'all'>, ListenerKind> = {
	execution: 'EXECUTION_LISTENER',
	task: 'TASK_LISTENER',
};

const ListenersTab: React.FC = () => {
	const {t} = useTranslation();
	const {processInstanceId} = useProcessInstancePage();
	const {
		hasSelection,
		selectedElementId,
		selectedElementInstanceKey,
		selectedElementInstanceCount,
		resolvedElementInstance,
		isFetchingElementError,
		refetchElement,
	} = useProcessInstanceElementSelection();
	const [selectedOption, setSelectedOption] = useState<FilterOption>('all');
	const hasUserTaskSelected = resolvedElementInstance?.type === 'USER_TASK';
	const activeOption = hasUserTaskSelected ? selectedOption : 'all';
	const elementInstanceKey = hasSelection
		? (selectedElementInstanceKey ?? resolvedElementInstance?.elementInstanceKey ?? null)
		: processInstanceId;
	const isSelectionResolved =
		!hasSelection || selectedElementInstanceKey !== null || selectedElementInstanceCount !== null;
	const {
		data,
		isLoading,
		isError,
		isFetchNextPageError,
		isPlaceholderData,
		hasNextPage,
		isFetchingNextPage,
		fetchNextPage,
		refetch: refetchListeners,
	} = useInfiniteQuery({
		...listenersQuery({
			processInstanceKey: processInstanceId,
			elementId: selectedElementId,
			elementInstanceKey,
			kind: activeOption === 'all' ? undefined : LISTENER_KIND[activeOption],
		}),
		enabled: isSelectionResolved && !isFetchingElementError,
		placeholderData: (previousData, previousQuery) => {
			const [, previousProcessInstanceKey, previousElementId, previousElementInstanceKey] =
				previousQuery?.queryKey ?? [];
			const isSameScope =
				previousProcessInstanceKey === processInstanceId &&
				previousElementId === selectedElementId &&
				previousElementInstanceKey === elementInstanceKey;
			return isSameScope ? previousData : undefined;
		},
	});
	const hasError = (isError && !isFetchNextPageError) || isFetchingElementError;
	const listeners = hasError ? [] : (data?.pages.flatMap((page) => page.items) ?? []);
	const hasNextPageError = isFetchNextPageError && !hasError;
	const isPending = !hasError && (isLoading || isPlaceholderData || !isSelectionResolved);

	const emptyMessage =
		hasUserTaskSelected && activeOption === 'all'
			? t('operate.processInstance.listeners.emptyAll')
			: hasUserTaskSelected && activeOption === 'task'
				? t('operate.processInstance.listeners.emptyTask')
				: t('operate.processInstance.listeners.emptyExecution');

	return (
		<div className="relative flex h-full min-h-0 flex-col">
			{hasUserTaskSelected && (
				<div className="flex w-52 flex-col gap-1.5 px-4 py-2">
					<Label htmlFor="listenerTypeFilter" className="sr-only">
						{t('operate.processInstance.listeners.listenerType')}
					</Label>
					<Select
						value={activeOption}
						onValueChange={(option) => setSelectedOption(option as FilterOption)}
						disabled={activeOption === 'all' && listeners.length === 0}
					>
						<SelectTrigger id="listenerTypeFilter" size="sm" className="w-full">
							<SelectValue />
						</SelectTrigger>
						<SelectContent>
							{FILTER_OPTIONS.map((option) => (
								<SelectItem key={option} value={option}>
									{t(`operate.processInstance.listeners.filter.${option}`)}
								</SelectItem>
							))}
						</SelectContent>
					</Select>
				</div>
			)}
			<div className="flex min-h-0 flex-1 flex-col" aria-busy={isPending || undefined}>
				{isPending ? (
					<div className="flex flex-col gap-2 p-4">
						<span className="sr-only">{t('operate.processInstance.listeners.loading')}</span>
						{SKELETON_ROWS.map((row) => (
							<Skeleton key={row} aria-hidden="true" className="h-8 w-full" />
						))}
					</div>
				) : listeners.length > 0 ? (
					<StructuredList
						label={t('operate.processInstance.listeners.list')}
						minWidth="48rem"
						isFlush={false}
						cellClassName="align-middle"
						containerClassName="rounded-none border-0 shadow-none"
						headerColumns={[
							{cellContent: t('operate.processInstance.listeners.listenerType'), width: '20%'},
							{cellContent: t('operate.processInstance.listeners.listenerKey'), width: '20%'},
							{cellContent: t('operate.processInstance.listeners.state'), width: '10%'},
							{cellContent: t('operate.processInstance.listeners.jobType'), width: '15%'},
							{cellContent: t('operate.processInstance.listeners.event'), width: '15%'},
							{cellContent: t('operate.processInstance.listeners.time'), width: '20%'},
						]}
						onVerticalScrollEndReach={() => {
							if (hasNextPage && !isFetchingNextPage) {
								void fetchNextPage();
							}
						}}
						rows={listeners.map(({kind, jobKey, state, type, listenerEventType, endTime}) => ({
							key: jobKey,
							columns: [
								{
									cellContent: (
										<div className="flex min-w-0 items-center gap-3">
											<TruncatedText text={spaceAndCapitalize(kind)} />
											{state === 'FAILED' && <StatusIcon variant="danger" />}
										</div>
									),
								},
								{cellContent: jobKey, className: 'break-all'},
								{cellContent: spaceAndCapitalize(state)},
								{cellContent: <TruncatedText text={type} />},
								{cellContent: spaceAndCapitalize(listenerEventType)},
								{cellContent: formatTimestamp(endTime)},
							],
						}))}
					/>
				) : hasError ? (
					<DataLoadErrorAlert onRetry={() => void (isFetchingElementError ? refetchElement() : refetchListeners())} />
				) : (
					<CenteredContent>
						<EmptyMessage message={emptyMessage} />
					</CenteredContent>
				)}
				{hasNextPageError && listeners.length > 0 && (
					<p role="alert" className="shrink-0 px-4 py-2 text-sm text-danger-foreground-subtle">
						{t('operate.shared.errorMessage.message')}
					</p>
				)}
			</div>
		</div>
	);
};

export {ListenersTab};
