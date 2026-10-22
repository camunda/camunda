/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
	createContext,
	useCallback,
	useContext,
	useEffect,
	useMemo,
	useRef,
	useState,
	type ComponentProps,
	type FocusEvent,
	type RefCallback,
} from 'react';
import {useQuery} from '@tanstack/react-query';
import {
	ActionableNotification,
	FeatureFlags,
	Toggle,
	InlineNotification,
	SkeletonText,
	Tag,
	TreeView,
} from '@carbon/react';
import {useTranslation} from 'react-i18next';
import type {ElementInstance, QueryBatchOperationItemsResponseBody} from '@camunda/camunda-api-zod-schemas/8.11';
import type {BusinessObject, BusinessObjects} from 'bpmn-js/lib/NavigatedViewer';
import {endpoints} from '#/shared/http/endpoints';
import {ForbiddenError} from '#/shared/errors';
import {InfiniteScroller} from '#/operate/shared/InfiniteScroller/InfiniteScroller';
import {StateIcon} from '#/operate/shared/StateIcon/StateIcon';
import {formatTimestamp} from '#/operate/shared/utils/formatTimestamp';
import {useDiagramXml} from '#/operate/pages/Processes/useDiagramXml';
import {useProcessInstancePage} from './useProcessInstancePage';
import {useProcessInstanceElementSelection} from './useProcessInstanceElementSelection';
import {useProcessInstanceModification} from './useProcessInstanceModification';
import {createFinishAddingTokenAction} from './modifications/modificationOperations';
import {canBeAddedTokenAncestor, getRunningInstancesByElement} from './modifications/modificationTargets';
import {getModificationsByElement, hasPendingCancelOrMoveModification} from './modifications/modificationSelectors';
import {
	getChildPlaceholders,
	getModificationPlaceholders,
	hasChildPlaceholders,
	type HistoryPlaceholder,
} from './modifications/historyPlaceholders';
import {useInstanceStatistics} from './instanceDiagram.queries';
import {isMultiInstance} from '#/operate/shared/utils/elements';
import {useInstanceHistory} from './useInstanceHistory';
import {instanceRequest} from './processInstance.queries';
import {ElementInstanceIcon} from './ElementInstanceIcon';
import {FilteredInstanceHistory} from './FilteredInstanceHistory';
import {InstanceHistorySearch} from './InstanceHistorySearch';
import {
	HistoryModificationIcons,
	HistoryAddIcon,
	HistoryCancelIcon,
	HistoryWarningIcon,
	HistoryPanel,
	HistoryHeader,
	HistoryScroll,
	HistoryTreeNode,
	HistoryIcon,
	HistoryBar,
	HistoryState,
	HistoryName,
	HistoryMetadata,
	HistoryTimestamp,
	HistoryStatus,
} from './instanceHistory.styled';
const ROW_HEIGHT = 32;
const foldable = new Set([
	'PROCESS',
	'MULTI_INSTANCE_BODY',
	'SUB_PROCESS',
	'EVENT_SUB_PROCESS',
	'AD_HOC_SUB_PROCESS',
	'AD_HOC_SUB_PROCESS_INNER_INSTANCE',
]);
type TabStop = {id: string; ancestors: string[]};
const NodeIconContext = createContext<{businessObject?: BusinessObject; root: boolean; leaf: boolean}>({
	root: false,
	leaf: false,
});
const HistoryModificationContext = createContext<{
	placeholders: HistoryPlaceholder[];
	modificationsByElement: ReturnType<typeof getModificationsByElement>;
	expandedPlaceholders: string[];
	togglePlaceholder: (key: string) => void;
}>({placeholders: [], modificationsByElement: {}, expandedPlaceholders: [], togglePlaceholder: () => {}});
function isInferredParentScope({state, type}: Pick<ElementInstance, 'state' | 'type'>) {
	return state === 'ACTIVE' && type !== 'MULTI_INSTANCE_BODY';
}
function getPlaceholderTreeKey(placeholderKey: string, parentTreeKey: string) {
	return `${placeholderKey}@${parentTreeKey}`;
}
function nodeId(key: string) {
	return `instance-history-${key}`;
}
// Carbon renders the icon slot itself; the legacy layout ignores Carbon's icon class and offsets leaf icons.
function NodeIcon() {
	const {businessObject, root, leaf} = useContext(NodeIconContext);
	return (
		<HistoryIcon $leaf={leaf}>
			<ElementInstanceIcon businessObject={businessObject} root={root} />
		</HistoryIcon>
	);
}
// InfiniteScroller observes the rows of the element it receives, so hand it the tree node's child group.
function HistoryTreeItem({ref, ...props}: ComponentProps<typeof HistoryTreeNode> & {ref?: RefCallback<Element>}) {
	const observeGroup = useCallback(
		(node: HTMLElement | null) => ref?.(node?.querySelector(':scope > [role="group"]') ?? null),
		[ref],
	);
	return <HistoryTreeNode {...props} ref={ref ? observeGroup : undefined} />;
}
function InstanceHistory({showHeader = true}: {showHeader?: boolean}) {
	const {processInstance, processInstanceId, search} = useProcessInstancePage();
	const {t} = useTranslation();
	const history = useInstanceHistory();
	const {isModificationModeEnabled, state: modificationState} = useProcessInstanceModification();
	const selection = useProcessInstanceElementSelection();
	const searchText = (search.elementSearch ?? '').trim();
	const isFiltered = !isModificationModeEnabled && (searchText !== '' || search.elementStatus !== undefined);
	const xml = useDiagramXml(processInstance.processDefinitionKey);
	const businessObjects = xml.data?.businessObjects;
	const statistics = useInstanceStatistics(
		processInstance,
		isModificationModeEnabled && xml.data !== undefined && xml.data.xml !== '',
	);
	const {placeholders, modificationsByElement} = useMemo(() => {
		const runningInstancesByElement = getRunningInstancesByElement(statistics.data, businessObjects);
		return {
			placeholders:
				isModificationModeEnabled && businessObjects !== undefined
					? getModificationPlaceholders(
							modificationState,
							businessObjects,
							processInstance.processDefinitionId,
							processInstanceId,
						)
					: [],
			modificationsByElement: getModificationsByElement(modificationState, {
				businessObjects,
				totalRunningInstancesByElement: runningInstancesByElement,
				totalRunningInstancesVisibleByElement: runningInstancesByElement,
			}),
		};
	}, [
		statistics.data,
		businessObjects,
		isModificationModeEnabled,
		modificationState,
		processInstance.processDefinitionId,
		processInstanceId,
	]);
	const placeholderSession = `${processInstanceId}/${isModificationModeEnabled}`;
	const [expanded, setExpanded] = useState({session: placeholderSession, keys: [] as string[]});
	const expandedPlaceholders = expanded.session === placeholderSession ? expanded.keys : [];
	const togglePlaceholder = (key: string) =>
		setExpanded({
			session: placeholderSession,
			keys: expandedPlaceholders.includes(key)
				? expandedPlaceholders.filter((expandedKey) => expandedKey !== key)
				: [...expandedPlaceholders, key],
		});
	const scrollRef = useRef<HTMLDivElement>(null);
	const [tabStop, setTabStop] = useState<TabStop>();
	const focus = useRef<{within: boolean; element: HTMLElement | null; target: string}>({
		within: false,
		element: null,
		target: '',
	});
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
	const rootId = nodeId(processInstanceId);
	const visible = new Set<string>();
	const selected: string[] = [];
	(function collect(item: ElementInstance) {
		const id = nodeId(item.elementInstanceKey);
		visible.add(id);
		if (
			item === root
				? !selection.hasSelection
				: selection.isSelected(item.elementId, item.elementInstanceKey, item.type === 'MULTI_INSTANCE_BODY')
		) {
			selected.push(id);
		}
		if (foldable.has(item.type)) {
			const window = history.windows.get(item.elementInstanceKey);
			if (window?.scope.from === 0) {
				collectPlaceholders(
					getChildPlaceholders(placeholders, item.elementInstanceKey, item.elementId, isInferredParentScope(item)),
					item.elementInstanceKey,
				);
			}
			window?.query.data?.items.forEach(collect);
		}
	})(root);
	function collectPlaceholders(children: HistoryPlaceholder[], parentTreeKey: string) {
		children.forEach(({elementId, elementInstanceKey}) => {
			const treeKey = getPlaceholderTreeKey(elementInstanceKey, parentTreeKey);
			const id = nodeId(treeKey);
			visible.add(id);
			if (selection.isSelected(elementId, elementInstanceKey, isMultiInstance(businessObjects?.[elementId]))) {
				selected.push(id);
			}
			if (expandedPlaceholders.includes(treeKey)) {
				collectPlaceholders(getChildPlaceholders(placeholders, elementInstanceKey, elementId, true), treeKey);
			}
		});
	}
	const focusTarget = [tabStop?.id, ...(tabStop?.ancestors ?? [])].find((id) => id && visible.has(id)) ?? rootId;
	useEffect(() => {
		focus.current.target = focusTarget;
		const {within, element} = focus.current;
		if (within && element && !element.isConnected && document.activeElement === document.body) {
			document.getElementById(focusTarget)?.focus();
		}
		// Carbon's TreeView makes its first node tabbable after every render; keep a single roving tab stop.
		scrollRef.current?.querySelectorAll<HTMLElement>('[role="treeitem"]').forEach((item) => {
			item.tabIndex = item.id === focusTarget ? 0 : -1;
		});
	});
	function handleFocus(event: FocusEvent<HTMLDivElement>) {
		const item = event.target;
		if (item.getAttribute('role') !== 'treeitem') {
			return;
		}
		const ancestors: string[] = [];
		for (
			let parent = item.parentElement?.closest('[role="treeitem"]');
			parent;
			parent = parent.parentElement?.closest('[role="treeitem"]')
		) {
			ancestors.push(parent.id);
		}
		focus.current.within = true;
		focus.current.element = item;
		setTabStop({id: item.id, ancestors});
	}
	function handleBlur(event: FocusEvent<HTMLDivElement>) {
		const next = event.relatedTarget;
		if (next && event.currentTarget.contains(next)) {
			return;
		}
		if (next) {
			focus.current.within = false;
			return;
		}
		const item = event.target;
		setTimeout(() => {
			if (item.isConnected) {
				focus.current.within = false;
			} else if (focus.current.within && document.activeElement === document.body) {
				document.getElementById(focus.current.target)?.focus();
			}
		});
	}
	const rootQuery = history.windows.get(processInstanceId)?.query;
	const error = xml.error ?? (isFiltered ? undefined : (history.pageError ?? rootQuery?.error));
	const forbidden = history.forbidden || error instanceof ForbiddenError;
	return (
		<HistoryPanel aria-label={t('operate.processInstance.history.title')}>
			{showHeader && (
				<HistoryHeader>
					<h2>{t('operate.processInstance.history.title')}</h2>
					{!isModificationModeEnabled &&
						(['timestamps', 'executionCount'] as const).map((control) => {
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
			{xml.isSuccess && !isModificationModeEnabled && !forbidden && <InstanceHistorySearch key={processInstanceId} />}
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
			) : xml.isPending ? (
				<SkeletonText />
			) : (
				<>
					{isFiltered ? (
						<FilteredInstanceHistory
							processInstanceKey={processInstanceId}
							searchText={searchText}
							statusFilter={search.elementStatus}
							businessObjects={businessObjects}
						/>
					) : rootQuery?.isPending ? (
						<SkeletonText />
					) : (
						<HistoryScroll ref={scrollRef} onFocus={handleFocus} onBlur={handleBlur}>
							<FeatureFlags enableTreeviewControllable>
								<TreeView
									label={t('operate.processInstance.history.title')}
									hideLabel
									selected={selected}
									active={selected[0] ?? ''}
								>
									<HistoryModificationContext
										value={{placeholders, modificationsByElement, expandedPlaceholders, togglePlaceholder}}
									>
										<HistoryNode
											item={root}
											hierarchy={[processInstanceId]}
											scrollRef={scrollRef}
											focusTarget={focusTarget}
											migrationDate={migration.data}
											businessObjects={businessObjects}
										/>
									</HistoryModificationContext>
								</TreeView>
							</FeatureFlags>
						</HistoryScroll>
					)}
				</>
			)}
		</HistoryPanel>
	);
}
function HistoryNode({
	item,
	parent,
	hierarchy,
	scrollRef,
	focusTarget,
	migrationDate,
	businessObjects,
}: {
	item: ElementInstance;
	parent?: string;
	hierarchy: string[];
	scrollRef: React.RefObject<HTMLElement | null>;
	focusTarget: string;
	migrationDate?: string | null;
	businessObjects?: BusinessObjects;
}) {
	const history = useInstanceHistory();
	const selection = useProcessInstanceElementSelection();
	const {isModificationModeEnabled, state: modificationState, dispatch} = useProcessInstanceModification();
	const {placeholders, modificationsByElement} = useContext(HistoryModificationContext);
	const {t} = useTranslation();
	const key = item.elementInstanceKey;
	const id = nodeId(key);
	const hasCancelModification =
		modificationsByElement[item.elementId]?.areAllTokensCanceled === true ||
		hierarchy.some((elementInstanceKey) =>
			hasPendingCancelOrMoveModification(modificationState, {
				elementId: item.elementId,
				elementInstanceKey,
				modificationsByElement,
			}),
		);
	const root = item.type === 'PROCESS';
	const isFoldable = foldable.has(item.type);
	const window = isFoldable ? history.windows.get(key) : undefined;
	const businessObject = businessObjects?.[item.elementId];
	const icon = useMemo(() => ({businessObject, root, leaf: !isFoldable}), [businessObject, root, isFoldable]);
	const label =
		(item.elementName ?? item.elementId) +
		(item.type === 'MULTI_INSTANCE_BODY' ? t('operate.processInstance.history.multiInstance') : '');
	async function page(direction: 'next' | 'previous', compensate: (distance: number) => void) {
		const count = await history.page(key, direction);
		if (count > 0) {
			compensate(count * ROW_HEIGHT);
		}
	}
	function select() {
		if (root) {
			selection.clearSelection();
		} else if (modificationState.status === 'moving-token') {
			return;
		} else if (modificationState.status === 'adding-token') {
			const sourceElementId = modificationState.sourceElementIdForAddOperation;
			if (
				businessObjects !== undefined &&
				sourceElementId !== null &&
				!hasCancelModification &&
				canBeAddedTokenAncestor(businessObjects, sourceElementId, item)
			) {
				dispatch(
					createFinishAddingTokenAction(modificationState, {
						businessObjects,
						ancestorElementId: item.elementId,
						ancestorElementInstanceKey: key,
					}),
				);
			}
		} else {
			void selection.selectElementInstance(
				item,
				item.type === 'AD_HOC_SUB_PROCESS_INNER_INSTANCE' && window?.scope.from === 0
					? window.query.data?.items[0]?.elementId
					: undefined,
			);
		}
	}
	const isTimestampVisible = history.timestamps && !isModificationModeEnabled && item.endDate;
	const showMetadata = (root && migrationDate) || isTimestampVisible;
	const treeItem = (
		<HistoryTreeItem
			id={id}
			aria-label={label}
			tabIndex={id === focusTarget ? 0 : -1}
			renderIcon={NodeIcon}
			onSelect={select}
			{...(isFoldable && {isExpanded: Boolean(window), onToggle: () => history.toggle(key, parent)})}
			label={
				<HistoryBar>
					<HistoryState>
						<StateIcon state={item.hasIncident ? 'INCIDENT' : item.state} size={16} />
					</HistoryState>
					<HistoryName>{label}</HistoryName>
					{showMetadata && (
						<HistoryMetadata>
							{root && migrationDate && (
								<Tag type="green">
									{t('operate.processInstance.history.migrated', {date: formatTimestamp(migrationDate)})}
								</Tag>
							)}
							{isTimestampVisible && <HistoryTimestamp>{formatTimestamp(item.endDate)}</HistoryTimestamp>}
						</HistoryMetadata>
					)}
					{hasCancelModification && !item.endDate && (
						<HistoryModificationIcons>
							<HistoryCancelIcon data-testid="cancel-icon">
								<title>{t('operate.processInstance.modification.plannedCancelTitle')}</title>
							</HistoryCancelIcon>
						</HistoryModificationIcons>
					)}
				</HistoryBar>
			}
		>
			{isFoldable ? (
				<>
					{window?.scope.from === 0 &&
						getChildPlaceholders(placeholders, key, item.elementId, isInferredParentScope(item)).map((placeholder) => (
							<PlaceholderNode
								key={placeholder.elementInstanceKey}
								placeholder={placeholder}
								parentTreeKey={key}
								focusTarget={focusTarget}
								businessObjects={businessObjects}
							/>
						))}
					{window?.query.data?.items.map((child) => (
						<HistoryNode
							key={child.elementInstanceKey}
							item={child}
							parent={key}
							hierarchy={[...hierarchy, child.elementInstanceKey]}
							scrollRef={scrollRef}
							focusTarget={focusTarget}
							businessObjects={businessObjects}
						/>
					))}
					{window && (window.query.isPending || window.query.isError || window.error) ? (
						<HistoryStatus role="none">
							{window.query.isPending ? (
								<SkeletonText />
							) : (
								<InlineNotification
									kind="error"
									lowContrast
									hideCloseButton
									title={t('operate.processInstance.history.error')}
								/>
							)}
						</HistoryStatus>
					) : null}
				</>
			) : undefined}
		</HistoryTreeItem>
	);
	return (
		<NodeIconContext value={icon}>
			{isFoldable ? (
				<InfiniteScroller
					scrollableContainerRef={scrollRef}
					onVerticalScrollEndReach={(compensate) => void page('next', compensate)}
					onVerticalScrollStartReach={(compensate) => void page('previous', compensate)}
				>
					{treeItem}
				</InfiniteScroller>
			) : (
				treeItem
			)}
		</NodeIconContext>
	);
}
function PlaceholderNode({
	placeholder: {elementId, elementInstanceKey: key},
	parentTreeKey,
	focusTarget,
	businessObjects,
}: {
	placeholder: HistoryPlaceholder;
	parentTreeKey: string;
	focusTarget: string;
	businessObjects?: BusinessObjects;
}) {
	const selection = useProcessInstanceElementSelection();
	const {state: modificationState} = useProcessInstanceModification();
	const {placeholders, expandedPlaceholders, togglePlaceholder} = useContext(HistoryModificationContext);
	const {t} = useTranslation();
	const businessObject = businessObjects?.[elementId];
	const hasChildren = hasChildPlaceholders(placeholders, key);
	const icon = useMemo(() => ({businessObject, root: false, leaf: !hasChildren}), [businessObject, hasChildren]);
	if (businessObjects === undefined || businessObject === undefined) {
		return null;
	}
	const name = businessObject.name ?? elementId;
	const treeKey = getPlaceholderTreeKey(key, parentTreeKey);
	const id = nodeId(treeKey);
	function select() {
		if (modificationState.status === 'moving-token' || modificationState.status === 'adding-token') {
			return;
		}
		selection.selectPlaceholder(elementId, key, isMultiInstance(businessObject));
	}
	return (
		<NodeIconContext value={icon}>
			<HistoryTreeItem
				id={id}
				aria-label={t('operate.processInstance.modification.plannedPlaceholderLabel', {name})}
				tabIndex={id === focusTarget ? 0 : -1}
				renderIcon={NodeIcon}
				onSelect={select}
				{...(hasChildren && {
					isExpanded: expandedPlaceholders.includes(treeKey),
					onToggle: () => togglePlaceholder(treeKey),
				})}
				label={
					<HistoryBar>
						<HistoryName>{name}</HistoryName>
						<HistoryModificationIcons>
							<HistoryWarningIcon data-testid="warning-icon">
								<title>{t('operate.processInstance.modification.plannedWarningTitle')}</title>
							</HistoryWarningIcon>
							<HistoryAddIcon data-testid="add-icon">
								<title>{t('operate.processInstance.modification.plannedAddTitle')}</title>
							</HistoryAddIcon>
						</HistoryModificationIcons>
					</HistoryBar>
				}
			>
				{hasChildren
					? getChildPlaceholders(placeholders, key, elementId, true).map((child) => (
							<PlaceholderNode
								key={child.elementInstanceKey}
								placeholder={child}
								parentTreeKey={treeKey}
								focusTarget={focusTarget}
								businessObjects={businessObjects}
							/>
						))
					: undefined}
			</HistoryTreeItem>
		</NodeIconContext>
	);
}
export {InstanceHistory};
