/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useRef} from 'react';
import {useQuery} from '@tanstack/react-query';
import {ActionableNotification, Toggle, InlineNotification, SkeletonText, Tag} from '@carbon/react';
import {CaretDown, CaretRight} from '@carbon/react/icons';
import {useTranslation} from 'react-i18next';
import type {ElementInstance, QueryBatchOperationItemsResponseBody} from '@camunda/camunda-api-zod-schemas/8.11';
import type {BusinessObjects} from 'bpmn-js/lib/NavigatedViewer';
import {endpoints} from '#/shared/http/endpoints';
import {ForbiddenError} from '#/shared/errors';
import {InfiniteScroller} from '#/operate/shared/InfiniteScroller/InfiniteScroller';
import {StateIcon} from '#/operate/shared/StateIcon/StateIcon';
import {formatTimestamp} from '#/operate/shared/utils/formatTimestamp';
import {useDiagramXml} from '#/operate/pages/Processes/useDiagramXml';
import {useProcessInstancePage} from './useProcessInstancePage';
import {useProcessInstanceElementSelection} from './useProcessInstanceElementSelection';
import {useInstanceHistory} from './useInstanceHistory';
import {instanceRequest} from './processInstance.queries';
import {ElementInstanceIcon} from './ElementInstanceIcon';
import {
	HistoryPanel,
	HistoryHeader,
	HistoryScroll,
	HistoryRow,
	HistoryState,
	HistoryToggle,
	HistoryChildren,
	RowSelection,
} from './instanceHistory.styled';
const foldable = new Set([
	'PROCESS',
	'MULTI_INSTANCE_BODY',
	'SUB_PROCESS',
	'EVENT_SUB_PROCESS',
	'AD_HOC_SUB_PROCESS',
	'AD_HOC_SUB_PROCESS_INNER_INSTANCE',
]);
function InstanceHistory({showHeader = true}: {showHeader?: boolean}) {
	const {processInstance, processInstanceId} = useProcessInstancePage();
	const {t} = useTranslation();
	const history = useInstanceHistory();
	const xml = useDiagramXml(processInstance.processDefinitionKey);
	const scrollRef = useRef<HTMLDivElement>(null);
	const migration = useQuery({
		queryKey: ['instanceLastMigration', processInstanceId],
		queryFn: async ({signal}) => {
			const result = await instanceRequest<QueryBatchOperationItemsResponseBody>(
				endpoints.queryBatchOperationItems({
					filter: {
						processInstanceKey: processInstanceId,
						operationType: 'MIGRATE_PROCESS_INSTANCE',
						state: 'COMPLETED',
					},
					sort: [{field: 'processedDate', order: 'desc'}],
					page: {from: 0, limit: 1},
				}),
				signal,
			);
			return result.items[0]?.processedDate ?? null;
		},
		retry: false,
	});
	const setVisible = history.setVisible;
	useEffect(() => {
		if (scrollRef.current) {
			scrollRef.current.scrollTop = 0;
		}
		setVisible(xml.isSuccess);
		return () => setVisible(false);
	}, [setVisible, xml.isSuccess, processInstanceId]);
	const root: ElementInstance = {
		...processInstance,
		elementId: processInstance.processDefinitionId,
		elementName: processInstance.processDefinitionName,
		elementInstanceKey: processInstanceId,
		type: 'PROCESS',
		state: processInstance.state === 'SUSPENDED' ? 'ACTIVE' : processInstance.state,
		incidentKey: null,
	};
	const rootQuery = history.windows.get(processInstanceId)?.query;
	const error = xml.error ?? history.pageError ?? rootQuery?.error;
	const forbidden = history.forbidden || error instanceof ForbiddenError;
	return (
		<HistoryPanel aria-label={t('operate.processInstance.history.title')}>
			{showHeader && (
				<HistoryHeader>
					<h2>{t('operate.processInstance.history.title')}</h2>
					{(['timestamps', 'executionCount'] as const).map((control) => {
						const label = t(
							control === 'timestamps'
								? 'operate.processInstance.history.timestamps'
								: 'operate.processInstance.history.executionCount',
						);
						return (
							<Toggle
								key={control}
								id={`history-${control}`}
								aria-label={label}
								labelA={label}
								labelB={label}
								size="sm"
								toggled={history[control]}
								disabled={!xml.isSuccess}
								onToggle={control === 'timestamps' ? history.setTimestamps : history.setExecutionCount}
							/>
						);
					})}
				</HistoryHeader>
			)}
			{forbidden ? (
				<InlineNotification
					kind="error"
					lowContrast
					hideCloseButton
					title={t('operate.processInstance.history.forbidden')}
				/>
			) : error ? (
				<ActionableNotification
					kind="error"
					lowContrast
					hideCloseButton
					title={t('operate.processInstance.history.error')}
					actionButtonLabel={t('errorGenericErrorPageButtonLabel')}
					onActionButtonClick={() => {
						if (xml.isError) {
							void xml.refetch();
						} else {
							history.retry();
						}
					}}
				/>
			) : xml.isPending || rootQuery?.isPending ? (
				<SkeletonText />
			) : (
				<HistoryScroll ref={scrollRef}>
					<HistoryChildren>
						<HistoryNode
							item={root}
							scrollRef={scrollRef}
							migrationDate={migration.data}
							businessObjects={xml.data?.businessObjects}
						/>
					</HistoryChildren>
				</HistoryScroll>
			)}
		</HistoryPanel>
	);
}
function HistoryNode({
	item,
	parent,
	scrollRef,
	migrationDate,
	businessObjects,
	depth = 0,
}: {
	item: ElementInstance;
	parent?: string;
	scrollRef: React.RefObject<HTMLElement | null>;
	migrationDate?: string | null;
	businessObjects?: BusinessObjects;
	depth?: number;
}) {
	const history = useInstanceHistory();
	const selection = useProcessInstanceElementSelection();
	const {t} = useTranslation();
	const key = item.elementInstanceKey;
	const root = item.type === 'PROCESS';
	const window = history.windows.get(key);
	const rowRef = useRef<HTMLDivElement>(null);
	const selectionRef = useRef<HTMLButtonElement>(null);
	const label =
		(item.elementName ?? item.elementId) +
		(item.type === 'MULTI_INSTANCE_BODY' ? t('operate.processInstance.history.multiInstance') : '');
	async function page(direction: 'next' | 'previous', compensate: (distance: number) => void) {
		const count = await history.page(key, direction);
		if (count > 0) {
			compensate(count * (direction === 'previous' ? 32 : (rowRef.current?.getBoundingClientRect().height ?? 32)));
		}
	}
	const selected = root
		? !selection.hasSelection
		: selection.isSelected(item.elementId, key, item.type === 'MULTI_INSTANCE_BODY');
	const isFoldable = foldable.has(item.type);
	function select() {
		selectionRef.current?.focus();
		if (root) {
			selection.clearSelection();
		} else {
			void selection.selectElementInstance(
				item,
				item.type === 'AD_HOC_SUB_PROCESS_INNER_INSTANCE' ? window?.query.data?.items[0]?.elementId : undefined,
			);
		}
	}
	return (
		<li>
			<HistoryRow ref={rowRef} $selected={selected} $depth={depth} $foldable={isFoldable} onClick={select}>
				<HistoryState>
					<StateIcon state={item.hasIncident ? 'INCIDENT' : item.state} size={16} />
				</HistoryState>
				{isFoldable && (
					<HistoryToggle
						kind="ghost"
						size="sm"
						hasIconOnly
						renderIcon={window ? CaretDown : CaretRight}
						iconDescription={t(
							window ? 'operate.processInstance.history.collapse' : 'operate.processInstance.history.expand',
							{name: label},
						)}
						aria-expanded={Boolean(window)}
						onClick={(event) => {
							event.stopPropagation();
							history.toggle(key, parent);
						}}
					/>
				)}
				<RowSelection ref={selectionRef} type="button" aria-pressed={selected}>
					<ElementInstanceIcon businessObject={businessObjects?.[item.elementId]} root={root} />
					<span>{label}</span>
					{root && migrationDate && (
						<Tag type="green">
							{t('operate.processInstance.history.migrated', {date: formatTimestamp(migrationDate)})}
						</Tag>
					)}
					{history.timestamps && item.endDate && <Tag type="gray">{formatTimestamp(item.endDate)}</Tag>}
				</RowSelection>
			</HistoryRow>
			{window && (
				<div>
					{window.query.isPending ? (
						<SkeletonText />
					) : window.query.isError || window.error ? (
						<InlineNotification
							kind="error"
							lowContrast
							hideCloseButton
							title={t('operate.processInstance.history.error')}
						/>
					) : null}
					<InfiniteScroller
						scrollableContainerRef={scrollRef}
						onVerticalScrollEndReach={(compensate) => void page('next', compensate)}
						onVerticalScrollStartReach={(compensate) => void page('previous', compensate)}
					>
						<HistoryChildren>
							{window.query.data?.items.map((child) => (
								<HistoryNode
									key={child.elementInstanceKey}
									item={child}
									parent={key}
									scrollRef={scrollRef}
									businessObjects={businessObjects}
									depth={depth + 1}
								/>
							))}
						</HistoryChildren>
					</InfiniteScroller>
				</div>
			)}
		</li>
	);
}
export {InstanceHistory};
