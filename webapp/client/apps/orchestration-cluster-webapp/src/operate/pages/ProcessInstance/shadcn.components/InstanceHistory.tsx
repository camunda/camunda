/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useRef, type ReactNode} from 'react';
import {useQuery} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {Alert, Badge, Button, Label, Skeleton, Switch, TreeView, type TreeNodeDef} from '@camunda/design-system';
import type {ElementInstance, QueryBatchOperationItemsResponseBody} from '@camunda/camunda-api-zod-schemas/8.11';
import {endpoints} from '#/shared/http/endpoints';
import {ForbiddenError} from '#/shared/errors';
import {formatTimestamp} from '#/operate/shared/utils/formatTimestamp';
import {PanelHeader} from '#/operate/shared/PanelHeader/shadcn.components/PanelHeader';
import {useDiagramXml} from '#/operate/pages/Processes/useDiagramXml';
import {useProcessInstancePage} from '../useProcessInstancePage';
import {useProcessInstanceElementSelection} from '../useProcessInstanceElementSelection';
import {useInstanceHistory} from '../useInstanceHistory';
import {instanceRequest} from '../processInstance.queries';
import {FilteredInstanceHistory} from './FilteredInstanceHistory';
import {HistoryPagingSentinel} from './HistoryPagingSentinel';
import {HistoryRowLabel} from './HistoryRowLabel';
import {InstanceHistorySearch} from './InstanceHistorySearch';

const ROW_HEIGHT = 32;
const FOLDABLE_TYPES = new Set([
	'PROCESS',
	'MULTI_INSTANCE_BODY',
	'SUB_PROCESS',
	'EVENT_SUB_PROCESS',
	'AD_HOC_SUB_PROCESS',
	'AD_HOC_SUB_PROCESS_INNER_INSTANCE',
]);

function nodeId(key: string) {
	return `instance-history-${key}`;
}

type NodeEntry = {item: ElementInstance; parent?: string};

type Props = {
	showHeader?: boolean;
};

const InstanceHistory: React.FC<Props> = ({showHeader = true}) => {
	const {processInstance, processInstanceId, search} = useProcessInstancePage();
	const {t} = useTranslation();
	const history = useInstanceHistory();
	const selection = useProcessInstanceElementSelection();
	const searchText = (search.elementSearch ?? '').trim();
	const isFiltered = searchText !== '' || search.elementStatus !== undefined;
	const xml = useDiagramXml(processInstance.processDefinitionKey);
	const businessObjects = xml.data?.businessObjects;
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
					sort: [{field: 'processedDate', order: 'DESC'}],
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
		setVisible(xml.isSuccess && !isFiltered);
		return () => setVisible(false);
	}, [setVisible, xml.isSuccess, isFiltered, processInstanceId]);

	const root: ElementInstance = {
		...processInstance,
		elementId: processInstance.processDefinitionId,
		elementName: processInstance.processDefinitionName ?? processInstance.processDefinitionId,
		elementInstanceKey: processInstanceId,
		type: 'PROCESS',
		state: processInstance.state === 'SUSPENDED' ? 'ACTIVE' : processInstance.state,
		incidentKey: null,
	};

	const entries = new Map<string, NodeEntry>();
	const selected = new Set<string>();

	async function page(key: string, direction: 'next' | 'previous') {
		const count = await history.page(key, direction);
		if (count > 0) {
			scrollRef.current?.scrollBy({top: (direction === 'next' ? -count : count) * ROW_HEIGHT});
		}
	}

	function buildNode(item: ElementInstance, parent?: string): TreeNodeDef {
		const key = item.elementInstanceKey;
		const id = nodeId(key);
		const isRoot = item.type === 'PROCESS';
		entries.set(id, {item, parent});
		if (
			isRoot ? !selection.hasSelection : selection.isSelected(item.elementId, key, item.type === 'MULTI_INSTANCE_BODY')
		) {
			selected.add(id);
		}

		const showTimestamp = history.timestamps;
		const migrationDate = isRoot ? migration.data : undefined;
		const label = (
			<span className="flex min-w-0 flex-1 items-center gap-2">
				<HistoryRowLabel
					item={item}
					businessObject={businessObjects?.[item.elementId]}
					suffix={item.type === 'MULTI_INSTANCE_BODY' ? t('operate.processInstance.history.multiInstance') : undefined}
					showTimestamp={showTimestamp}
				/>
				{migrationDate && (
					<Badge variant="success">
						{t('operate.processInstance.history.migrated', {date: formatTimestamp(migrationDate)})}
					</Badge>
				)}
			</span>
		);

		if (!FOLDABLE_TYPES.has(item.type)) {
			return {id, label};
		}

		const window = history.windows.get(key);
		if (!window) {
			return {id, label, isExpanded: false, children: [{id: `${id}-collapsed`, label: null, disabled: true}]};
		}

		const data = window.query.data;
		const hasNextPage = data
			? data.page.hasMoreTotalItems
				? data.items.length === 100
				: window.scope.from + 100 < data.page.totalItems
			: false;
		const children: TreeNodeDef[] = [];
		if (data && window.scope.from > 0) {
			children.push({
				id: `${id}-previous`,
				label: <HistoryPagingSentinel scrollRef={scrollRef} onReach={() => void page(key, 'previous')} />,
			});
		}
		data?.items.forEach((child) => children.push(buildNode(child, key)));
		if (hasNextPage) {
			children.push({
				id: `${id}-next`,
				label: <HistoryPagingSentinel scrollRef={scrollRef} onReach={() => void page(key, 'next')} />,
			});
		}
		if (window.query.isPending) {
			children.push({id: `${id}-loading`, label: <Skeleton className="h-4 w-full" />});
		} else if (window.query.isError || window.error) {
			children.push({
				id: `${id}-error`,
				label: <Alert variant="destructive" title={t('operate.processInstance.history.error')} className="w-full" />,
			});
		}
		return {id, label, isExpanded: true, children};
	}

	const rootQuery = history.windows.get(processInstanceId)?.query;
	const error = xml.error ?? (isFiltered ? undefined : (history.pageError ?? rootQuery?.error));
	const forbidden = history.forbidden || error instanceof ForbiddenError;

	let content: ReactNode;
	if (forbidden) {
		content = (
			<Alert variant="destructive" title={t('operate.processInstance.history.forbidden')} className="m-2 w-auto" />
		);
	} else if (error) {
		content = (
			<div className="flex flex-col items-start gap-2 p-2">
				<Alert variant="destructive" title={t('operate.processInstance.history.error')} />
				<Button
					variant="secondary"
					size="sm"
					onClick={() => {
						if (xml.isError) {
							void xml.refetch();
						} else {
							history.retry();
						}
					}}
				>
					{t('errorGenericErrorPageButtonLabel')}
				</Button>
			</div>
		);
	} else if (xml.isPending) {
		content = <Skeleton className="m-2 h-6" />;
	} else if (isFiltered) {
		content = (
			<FilteredInstanceHistory
				processInstanceKey={processInstanceId}
				searchText={searchText}
				statusFilter={search.elementStatus}
				businessObjects={businessObjects}
			/>
		);
	} else if (rootQuery?.isPending) {
		content = <Skeleton className="m-2 h-6" />;
	} else {
		const data = [buildNode(root)];
		content = (
			<div ref={scrollRef} className="min-h-0 flex-1 overflow-y-auto [overflow-anchor:none]">
				<TreeView
					label={t('operate.processInstance.history.title')}
					hideLabel
					size="sm"
					data={data}
					selected={selected}
					onSelect={(_, node) => {
						const entry = entries.get(node.id);
						if (!entry) {
							return;
						}
						if (entry.item.type === 'PROCESS') {
							selection.clearSelection();
							return;
						}
						const window = history.windows.get(entry.item.elementInstanceKey);
						void selection.selectElementInstance(
							entry.item,
							entry.item.type === 'AD_HOC_SUB_PROCESS_INNER_INSTANCE' && window?.scope.from === 0
								? window.query.data?.items[0]?.elementId
								: undefined,
						);
					}}
					onToggle={(node) => {
						const entry = entries.get(node.id);
						if (entry) {
							history.toggle(entry.item.elementInstanceKey, entry.parent);
						}
					}}
				/>
			</div>
		);
	}

	return (
		<section
			aria-label={t('operate.processInstance.history.title')}
			className="flex h-full min-h-0 min-w-0 flex-col bg-background"
		>
			{showHeader && (
				<PanelHeader title={t('operate.processInstance.history.title')} size="sm">
					<div className="ml-auto flex items-center gap-4">
						{(['timestamps', 'executionCount'] as const).map((control) => {
							const label = t(`operate.processInstance.history.${control}`);
							return (
								<div key={control} className="flex items-center gap-2">
									<Switch
										id={`history-${control}`}
										size="sm"
										checked={history[control]}
										disabled={!xml.isSuccess}
										onCheckedChange={control === 'timestamps' ? history.setTimestamps : history.setExecutionCount}
									/>
									<Label htmlFor={`history-${control}`}>{label}</Label>
								</div>
							);
						})}
					</div>
				</PanelHeader>
			)}
			{xml.isSuccess && !forbidden && <InstanceHistorySearch key={processInstanceId} />}
			{content}
		</section>
	);
};

export {InstanceHistory};
