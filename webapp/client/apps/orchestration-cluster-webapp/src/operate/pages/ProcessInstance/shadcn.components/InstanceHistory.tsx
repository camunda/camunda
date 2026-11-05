/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useEffect, useMemo, useRef, type ReactNode} from 'react';
import {useQuery} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {Alert, Badge, Button, Label, Skeleton, Switch, TreeView, type TreeNodeDef} from '@camunda/design-system';
import {Ban, Plus, TriangleAlert} from '@camunda/design-system/icons';
import type {ElementInstance} from '@camunda/camunda-api-zod-schemas/8.11';
import {ForbiddenError} from '#/shared/errors';
import {isMultiInstance} from '#/operate/shared/utils/elements';
import {formatTimestamp} from '#/operate/shared/utils/formatTimestamp';
import {PanelHeader} from '#/operate/shared/PanelHeader/shadcn.components/PanelHeader';
import {useDiagramXml} from '#/operate/pages/Processes/useDiagramXml';
import {useProcessInstancePage} from '../useProcessInstancePage';
import {useProcessInstanceElementSelection} from '../useProcessInstanceElementSelection';
import {useProcessInstanceModification} from '../useProcessInstanceModification';
import {useInstanceHistory} from '../useInstanceHistory';
import {getPlaceholderTreeKey, useHistoryModification} from '../useHistoryModification';
import {
	getChildPlaceholders,
	hasChildPlaceholders,
	type HistoryPlaceholder,
} from '../modifications/historyPlaceholders';
import {instanceLastMigrationQuery} from '../processInstance.queries';
import {FilteredInstanceHistory} from './FilteredInstanceHistory';
import {HistoryPagingSentinel} from './HistoryPagingSentinel';
import {ElementInstanceIcon} from './ElementInstanceIcon';
import {HISTORY_TREE_CLASS_NAME, HistoryRowLabel} from './HistoryRowLabel';
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

const HISTORY_CONTROLS = [
	{id: 'timestamps', labelKey: 'operate.processInstance.history.timestamps'},
	{id: 'executionCount', labelKey: 'operate.processInstance.history.executionCount'},
] as const;

function nodeId(key: string) {
	return `instance-history-${key}`;
}

function isInferredParentScope({state, type}: Pick<ElementInstance, 'state' | 'type'>) {
	return state === 'ACTIVE' && type !== 'MULTI_INSTANCE_BODY';
}

type NodeEntry = {item: ElementInstance; parent?: string; hierarchy: string[]};
type PlaceholderEntry = {placeholder: HistoryPlaceholder; treeKey: string};

type Props = {
	showHeader?: boolean;
};

const InstanceHistory: React.FC<Props> = ({showHeader = true}) => {
	const {processInstance, processInstanceId, search} = useProcessInstancePage();
	const {t} = useTranslation();
	const history = useInstanceHistory();
	const selection = useProcessInstanceElementSelection();
	const {isModificationModeEnabled} = useProcessInstanceModification();
	const searchText = (search.elementSearch ?? '').trim();
	const isFiltered = !isModificationModeEnabled && (searchText !== '' || search.elementStatus !== undefined);
	const xml = useDiagramXml(processInstance.processDefinitionKey);
	const businessObjects = xml.data?.businessObjects;
	const {
		placeholders,
		expandedPlaceholders,
		togglePlaceholder,
		hasPlannedCancel,
		handleModificationSelect,
		selectPlaceholder,
	} = useHistoryModification({
		businessObjects,
		isXmlLoaded: xml.data !== undefined && xml.data.xml !== '',
	});
	const scrollRef = useRef<HTMLDivElement>(null);

	const migration = useQuery(instanceLastMigrationQuery(processInstanceId));

	const setVisible = history.setVisible;
	useEffect(() => {
		if (scrollRef.current) {
			scrollRef.current.scrollTop = 0;
		}
		setVisible(xml.isSuccess && !isFiltered);
		return () => setVisible(false);
	}, [setVisible, xml.isSuccess, isFiltered, processInstanceId]);

	const root = useMemo<ElementInstance>(
		() => ({
			...processInstance,
			elementId: processInstance.processDefinitionId,
			elementName: processInstance.processDefinitionName ?? processInstance.processDefinitionId,
			elementInstanceKey: processInstanceId,
			type: 'PROCESS',
			state: processInstance.state === 'SUSPENDED' ? 'ACTIVE' : processInstance.state,
			incidentKey: null,
		}),
		[processInstance, processInstanceId],
	);

	const pageHistoryRef = useRef(history.page);
	useEffect(() => {
		pageHistoryRef.current = history.page;
	});
	const pageHistory = useCallback(async (key: string, direction: 'next' | 'previous') => {
		const count = await pageHistoryRef.current(key, direction);
		if (count > 0) {
			scrollRef.current?.scrollBy({top: (direction === 'next' ? -count : count) * ROW_HEIGHT});
		}
	}, []);

	const {isSelected, hasSelection} = selection;
	const {windows, timestamps} = history;
	const tree = useMemo(() => {
		const entries = new Map<string, NodeEntry>();
		const placeholderEntries = new Map<string, PlaceholderEntry>();
		const selected = new Set<string>();

		function buildPlaceholderNode(
			{elementId, elementInstanceKey}: HistoryPlaceholder,
			parentTreeKey: string,
		): TreeNodeDef[] {
			const businessObject = businessObjects?.[elementId];
			if (businessObject === undefined) {
				return [];
			}
			const name = businessObject.name ?? elementId;
			const treeKey = getPlaceholderTreeKey(elementInstanceKey, parentTreeKey);
			const id = nodeId(treeKey);
			placeholderEntries.set(id, {placeholder: {elementId, elementInstanceKey}, treeKey});
			if (isSelected(elementId, elementInstanceKey, isMultiInstance(businessObject))) {
				selected.add(id);
			}
			const node: TreeNodeDef = {
				id,
				label: (
					<span
						className="flex min-w-0 flex-1 items-center gap-2"
						aria-label={t('operate.processInstance.modification.plannedPlaceholderLabel', {name})}
					>
						<ElementInstanceIcon businessObject={businessObject} root={false} />
						<span className="min-w-0 flex-1 truncate">{name}</span>
						<TriangleAlert
							role="img"
							aria-label={t('operate.processInstance.modification.plannedWarningTitle')}
							className="size-4 shrink-0 text-warning-foreground-strong"
						/>
						<Plus
							role="img"
							aria-label={t('operate.processInstance.modification.plannedAddTitle')}
							className="size-4 shrink-0"
						/>
					</span>
				),
			};
			if (!hasChildPlaceholders(placeholders, elementInstanceKey)) {
				return [node];
			}
			const isExpanded = expandedPlaceholders.includes(treeKey);
			return [
				{
					...node,
					isExpanded,
					children: isExpanded
						? getChildPlaceholders(placeholders, elementInstanceKey, elementId, true).flatMap((child) =>
								buildPlaceholderNode(child, treeKey),
							)
						: [{id: `${id}-collapsed`, label: null, disabled: true}],
				},
			];
		}

		function buildNode(item: ElementInstance, hierarchy: string[], parent?: string): TreeNodeDef {
			const key = item.elementInstanceKey;
			const id = nodeId(key);
			const isRoot = item.type === 'PROCESS';
			const hasCancelModification = hasPlannedCancel(item, hierarchy) && !item.endDate;
			entries.set(id, {item, parent, hierarchy});
			if (isRoot ? !hasSelection : isSelected(item.elementId, key, item.type === 'MULTI_INSTANCE_BODY')) {
				selected.add(id);
			}

			const migrationDate = isRoot ? migration.data : undefined;
			const label = (
				<span className="flex min-w-0 flex-1 items-center gap-2">
					<HistoryRowLabel
						item={item}
						businessObject={businessObjects?.[item.elementId]}
						suffix={
							item.type === 'MULTI_INSTANCE_BODY' ? t('operate.processInstance.history.multiInstance') : undefined
						}
						showTimestamp={timestamps && !isModificationModeEnabled}
					/>
					{hasCancelModification && (
						<Ban
							role="img"
							aria-label={t('operate.processInstance.modification.plannedCancelTitle')}
							className="size-4 shrink-0 text-destructive"
						/>
					)}
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

			const scopeWindow = windows.get(key);
			if (!scopeWindow) {
				return {id, label, isExpanded: false, children: [{id: `${id}-collapsed`, label: null, disabled: true}]};
			}

			const data = scopeWindow.query.data;
			const hasNextPage = data
				? data.page.hasMoreTotalItems
					? data.items.length === 100
					: scopeWindow.scope.from + 100 < data.page.totalItems
				: false;
			const revision = `${scopeWindow.scope.from}:${data?.items.length ?? 0}`;
			const children: TreeNodeDef[] =
				scopeWindow.scope.from === 0
					? getChildPlaceholders(placeholders, key, item.elementId, isInferredParentScope(item)).flatMap(
							(placeholder) => buildPlaceholderNode(placeholder, key),
						)
					: [];
			if (data && scopeWindow.scope.from > 0) {
				children.push({
					id: `${id}-previous`,
					disabled: true,
					label: (
						<HistoryPagingSentinel
							scrollRef={scrollRef}
							revision={revision}
							onReach={() => void pageHistory(key, 'previous')}
						/>
					),
				});
			}
			data?.items.forEach((child) => children.push(buildNode(child, [...hierarchy, child.elementInstanceKey], key)));
			if (hasNextPage) {
				children.push({
					id: `${id}-next`,
					disabled: true,
					label: (
						<HistoryPagingSentinel
							scrollRef={scrollRef}
							revision={revision}
							onReach={() => void pageHistory(key, 'next')}
						/>
					),
				});
			}
			if (scopeWindow.query.isPending) {
				children.push({id: `${id}-loading`, label: <Skeleton className="h-4 w-full" />});
			} else if (scopeWindow.query.isError || scopeWindow.error) {
				children.push({
					id: `${id}-error`,
					label: <Alert variant="destructive" title={t('operate.processInstance.history.error')} className="w-full" />,
				});
			}
			return {id, label, isExpanded: true, children};
		}

		return {data: [buildNode(root, [processInstanceId])], entries, placeholderEntries, selected};
	}, [
		root,
		processInstanceId,
		windows,
		timestamps,
		isModificationModeEnabled,
		placeholders,
		expandedPlaceholders,
		hasPlannedCancel,
		hasSelection,
		isSelected,
		migration.data,
		businessObjects,
		t,
		pageHistory,
	]);

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
		content = (
			<div ref={scrollRef} className="min-h-0 flex-1 overflow-y-auto [overflow-anchor:none]">
				<TreeView
					label={t('operate.processInstance.history.title')}
					hideLabel
					size="sm"
					className={HISTORY_TREE_CLASS_NAME}
					data={tree.data}
					selected={tree.selected}
					onSelect={(_, node) => {
						const placeholderEntry = tree.placeholderEntries.get(node.id);
						if (placeholderEntry) {
							selectPlaceholder(
								placeholderEntry.placeholder.elementId,
								placeholderEntry.placeholder.elementInstanceKey,
							);
							return;
						}
						const entry = tree.entries.get(node.id);
						if (!entry) {
							return;
						}
						if (entry.item.type === 'PROCESS') {
							selection.clearSelection();
							return;
						}
						if (handleModificationSelect(entry.item, hasPlannedCancel(entry.item, entry.hierarchy))) {
							return;
						}
						const scopeWindow = history.windows.get(entry.item.elementInstanceKey);
						void selection.selectElementInstance(
							entry.item,
							entry.item.type === 'AD_HOC_SUB_PROCESS_INNER_INSTANCE' && scopeWindow?.scope.from === 0
								? scopeWindow.query.data?.items[0]?.elementId
								: undefined,
						);
					}}
					onToggle={(node) => {
						const placeholderEntry = tree.placeholderEntries.get(node.id);
						if (placeholderEntry) {
							togglePlaceholder(placeholderEntry.treeKey);
							return;
						}
						const entry = tree.entries.get(node.id);
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
				<PanelHeader title={t('operate.processInstance.history.title')}>
					{!isModificationModeEnabled && (
						<div className="ml-auto flex items-center gap-4">
							{HISTORY_CONTROLS.map(({id, labelKey}) => (
								<div key={id} className="flex items-center gap-2">
									<Switch
										id={`history-${id}`}
										size="sm"
										checked={history[id]}
										disabled={!xml.isSuccess}
										onCheckedChange={id === 'timestamps' ? history.setTimestamps : history.setExecutionCount}
									/>
									<Label htmlFor={`history-${id}`}>{t(labelKey)}</Label>
								</div>
							))}
						</div>
					)}
				</PanelHeader>
			)}
			{xml.isSuccess && !isModificationModeEnabled && !forbidden && <InstanceHistorySearch key={processInstanceId} />}
			{content}
		</section>
	);
};

export {InstanceHistory};
