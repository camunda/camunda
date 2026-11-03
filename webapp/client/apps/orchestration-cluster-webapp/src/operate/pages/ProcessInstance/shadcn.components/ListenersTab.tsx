/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {keepPreviousData, useInfiniteQuery} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {Label, Select, SelectContent, SelectItem, SelectTrigger, SelectValue, Spinner} from '@camunda/design-system';
import {TriangleAlert} from '@camunda/design-system/icons';
import {ErrorMessage} from '#/operate/shared/ErrorMessage/shadcn.components/ErrorMessage';
import {EmptyMessage} from '#/operate/shared/EmptyMessage/shadcn.components/EmptyMessage';
import {CenteredContent} from '#/operate/shared/CenteredContent/shadcn.components/CenteredContent';
import {TruncatedText} from '#/operate/shared/TruncatedText/shadcn.components/TruncatedText';
import {StructuredList} from '#/operate/shared/StructuredList/shadcn.components/StructuredList';
import {spaceAndCapitalize} from '#/operate/shared/utils/spaceAndCapitalize';
import {formatTimestamp} from '#/operate/shared/utils/formatTimestamp';
import {listenersQuery, type ListenerKind} from '../listeners.queries';
import {useProcessInstanceElementSelection} from '../useProcessInstanceElementSelection';
import {useProcessInstancePage} from '../useProcessInstancePage';

type FilterOption = 'all' | 'execution' | 'task';

const FILTER_OPTIONS: FilterOption[] = ['all', 'execution', 'task'];
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
	} = useInfiniteQuery({
		...listenersQuery({
			processInstanceKey: processInstanceId,
			elementId: selectedElementId,
			elementInstanceKey,
			kind: activeOption === 'all' ? undefined : LISTENER_KIND[activeOption],
		}),
		enabled: isSelectionResolved && !isFetchingElementError,
		placeholderData: keepPreviousData,
	});
	const hasError = isError || isFetchNextPageError || isFetchingElementError;
	const listeners = hasError || isPlaceholderData ? [] : (data?.pages.flatMap((page) => page.items) ?? []);
	const isPending = !hasError && (isLoading || isPlaceholderData || !isSelectionResolved);

	const emptyMessage =
		hasUserTaskSelected && activeOption === 'all'
			? t('operate.processInstance.listeners.emptyAll')
			: hasUserTaskSelected && activeOption === 'task'
				? t('operate.processInstance.listeners.emptyTask')
				: t('operate.processInstance.listeners.emptyExecution');

	return (
		<div className="relative flex h-full min-h-0 flex-col">
			{isPending && (
				<CenteredContent className="absolute inset-0 z-10 bg-background/60">
					<Spinner size="default" accessibleLabel={t('operate.processInstance.listeners.loading')} />
				</CenteredContent>
			)}
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
			<div className="flex min-h-0 flex-1 flex-col">
				{listeners.length > 0 ? (
					<StructuredList
						label={t('operate.processInstance.listeners.list')}
						className="[&_table]:min-w-[48rem] [&_table]:table-fixed"
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
											{state === 'FAILED' && (
												<TriangleAlert size={16} aria-hidden className="text-danger-foreground-subtle" />
											)}
										</div>
									),
								},
								{cellContent: <TruncatedText text={jobKey} />},
								{cellContent: spaceAndCapitalize(state)},
								{cellContent: <TruncatedText text={type} />},
								{cellContent: spaceAndCapitalize(listenerEventType)},
								{cellContent: formatTimestamp(endTime)},
							],
						}))}
					/>
				) : (
					!isPending && (
						<CenteredContent>{hasError ? <ErrorMessage /> : <EmptyMessage message={emptyMessage} />}</CenteredContent>
					)
				)}
			</div>
		</div>
	);
};

export {ListenersTab};
