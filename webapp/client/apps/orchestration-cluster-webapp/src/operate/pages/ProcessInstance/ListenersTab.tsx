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
import {Loading} from '@carbon/react';
import {EmptyMessage} from '#/operate/shared/EmptyMessage/EmptyMessage';
import {spaceAndCapitalize} from '#/operate/shared/utils/spaceAndCapitalize';
import {formatTimestamp} from '#/operate/shared/utils/formatTimestamp';
import {listenersQuery, type ListenerKind} from './listeners.queries';
import {
	CellContainer,
	Content,
	Dropdown,
	EmptyMessageWrapper,
	Stack,
	StructuredList,
	WarningFilled,
} from './listeners.styled';
import {useProcessInstanceElementSelection} from './useProcessInstanceElementSelection';
import {useProcessInstancePage} from './useProcessInstancePage';

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
		<Content>
			{isPending && <Loading data-testid="listeners-spinner" />}
			{hasUserTaskSelected && (
				<Dropdown
					id="listenerTypeFilter"
					data-testid="listener-type-filter"
					titleText={t('operate.processInstance.listeners.listenerType')}
					label={t('operate.processInstance.listeners.filter.all')}
					hideLabel
					items={FILTER_OPTIONS}
					itemToString={(option: FilterOption) => t(`operate.processInstance.listeners.filter.${option}`)}
					selectedItem={activeOption}
					onChange={({selectedItem}: {selectedItem: FilterOption}) => setSelectedOption(selectedItem)}
					size="sm"
					disabled={activeOption === 'all' && listeners.length === 0}
				/>
			)}
			<Stack>
				{listeners.length > 0 ? (
					<StructuredList
						dataTestId="listeners-list"
						label={t('operate.processInstance.listeners.list')}
						headerSize="sm"
						verticalCellPadding="var(--cds-spacing-02)"
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
							dataTestId: jobKey,
							columns: [
								{
									cellContent: (
										<CellContainer orientation="horizontal" gap={3}>
											{spaceAndCapitalize(kind)}
											{state === 'FAILED' && <WarningFilled />}
										</CellContainer>
									),
								},
								{cellContent: <CellContainer>{jobKey}</CellContainer>},
								{cellContent: <CellContainer>{spaceAndCapitalize(state)}</CellContainer>},
								{cellContent: <CellContainer>{type}</CellContainer>},
								{cellContent: <CellContainer>{spaceAndCapitalize(listenerEventType)}</CellContainer>},
								{cellContent: <CellContainer>{formatTimestamp(endTime)}</CellContainer>},
							],
						}))}
					/>
				) : (
					!isPending && (
						<EmptyMessageWrapper>
							<EmptyMessage message={emptyMessage} />
						</EmptyMessageWrapper>
					)
				)}
			</Stack>
		</Content>
	);
};

export {ListenersTab};
