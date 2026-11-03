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
import {EmptyMessage} from '#/operate/shared/EmptyMessage/shadcn.components/EmptyMessage';
import {CenteredContent} from '#/operate/shared/CenteredContent/shadcn.components/CenteredContent';
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
	const {data, isLoading, hasNextPage, isFetchingNextPage, fetchNextPage} = useInfiniteQuery({
		...listenersQuery({
			processInstanceKey: processInstanceId,
			elementId: selectedElementId,
			elementInstanceKey,
			kind: activeOption === 'all' ? undefined : LISTENER_KIND[activeOption],
		}),
		enabled: isSelectionResolved && !isFetchingElementError,
		placeholderData: keepPreviousData,
	});
	const listeners = isFetchingElementError ? [] : (data?.pages.flatMap((page) => page.items) ?? []);
	const isPending = isLoading || (!isSelectionResolved && !isFetchingElementError);

	const emptyMessage =
		hasUserTaskSelected && activeOption === 'all'
			? t('operate.processInstance.listeners.emptyAll')
			: hasUserTaskSelected && activeOption === 'task'
				? t('operate.processInstance.listeners.emptyTask')
				: t('operate.processInstance.listeners.emptyExecution');

	return (
		<div className="relative flex h-full min-h-0 flex-col px-4">
			{isPending && (
				<CenteredContent className="absolute inset-0 z-10 bg-background/60">
					<Spinner size="default" accessibleLabel={t('operate.processInstance.listeners.loading')} />
				</CenteredContent>
			)}
			{hasUserTaskSelected && (
				<div className="flex w-52 flex-col gap-1.5 py-2">
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
						headerSize="sm"
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
										<div className="flex items-center gap-3">
											{spaceAndCapitalize(kind)}
											{state === 'FAILED' && (
												<TriangleAlert size={16} aria-hidden className="text-danger-foreground-subtle" />
											)}
										</div>
									),
								},
								{cellContent: jobKey},
								{cellContent: spaceAndCapitalize(state)},
								{cellContent: type},
								{cellContent: spaceAndCapitalize(listenerEventType)},
								{cellContent: formatTimestamp(endTime)},
							],
						}))}
					/>
				) : (
					!isPending && (
						<CenteredContent>
							<EmptyMessage message={emptyMessage} />
						</CenteredContent>
					)
				)}
			</div>
		</div>
	);
};

export {ListenersTab};
