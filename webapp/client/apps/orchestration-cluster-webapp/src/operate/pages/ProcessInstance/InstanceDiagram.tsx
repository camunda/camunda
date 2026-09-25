/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useEffect, useMemo, useRef, useState} from 'react';
import {useNavigate, useRouter} from '@tanstack/react-router';
import {useTranslation} from 'react-i18next';
import {useQueryClient as useReactQueryClient} from '@tanstack/react-query';
import kebabCase from 'lodash/kebabCase';
import {ForbiddenError} from '#/shared/errors';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {Diagram} from '#/operate/shared/Diagram';
import {DiagramShell} from '#/operate/shared/DiagramShell/DiagramShell';
import {isInstanceRunning} from '#/operate/shared/utils/processInstance';
import {useDiagramXml} from '#/operate/pages/Processes/useDiagramXml';
import {useProcessInstanceWaitStateStatistics} from './processInstance.queries';
import {useProcessInstancePage} from './useProcessInstancePage';
import {processInstanceSearchSchema} from './processInstanceSearch';
import {
	useInstanceDiagramData,
	elementDrilldownQuery,
	calledProcessQuery,
	calledDecisionQuery,
} from './instanceDiagram.queries';
import {useInstanceDiagramOverlays, type ModificationBadge} from './useInstanceDiagramOverlays';
import {InstanceDiagramOverlays} from './InstanceDiagramOverlays';
import {Panel} from './instanceDiagram.styled';

const NO_MODIFICATION_BADGES: ModificationBadge[] = [];

type Props = {
	isModificationModeEnabled?: boolean;
	isExecutionCountVisible?: boolean;
	modifiableElements?: string[];
	modificationBadges?: ModificationBadge[];
	selectedElementOverlay?: React.ComponentProps<typeof Diagram>['selectedElementOverlay'];
	onModificationElementSelection?: (elementId?: string, isMultiInstance?: boolean) => void;
};

function InstanceDiagram({
	isModificationModeEnabled = false,
	isExecutionCountVisible = false,
	modifiableElements,
	modificationBadges = NO_MODIFICATION_BADGES,
	selectedElementOverlay,
	onModificationElementSelection,
}: Props) {
	const {processInstanceId, processInstance, selection, search} = useProcessInstancePage();
	const {t} = useTranslation();
	const navigate = useNavigate();
	const router = useRouter();
	const queryClient = useReactQueryClient();
	const [pendingDrillDownElementId, setPendingDrillDownElementId] = useState<string | null>(null);
	const drillDownVersion = useRef(0);
	const previousInstanceId = useRef(processInstanceId);
	const currentInstanceId = useRef<string | null>(processInstanceId);
	const selectedElementId = selection.anchorElementId ?? selection.elementId;
	const previousSelection = useRef({elementId: selectedElementId, elementInstanceKey: selection.elementInstanceKey});
	useEffect(() => {
		if (
			previousSelection.current.elementId !== selectedElementId ||
			previousSelection.current.elementInstanceKey !== selection.elementInstanceKey
		) {
			drillDownVersion.current++;
			setPendingDrillDownElementId(null);
		}
		previousSelection.current = {elementId: selectedElementId, elementInstanceKey: selection.elementInstanceKey};
	}, [selectedElementId, selection.elementInstanceKey]);
	const {
		data: diagram,
		isPending: isXmlPending,
		isError: isXmlError,
		error: xmlError,
		isFetching: isXmlFetching,
	} = useDiagramXml(processInstance.processDefinitionKey);
	const rootElement = diagram?.diagramModel?.rootElement;
	const hasDiagram =
		diagram?.xml !== '' &&
		rootElement !== undefined &&
		'diagrams' in rootElement &&
		Array.isArray(rootElement.diagrams) &&
		rootElement.diagrams.some(({plane}) => plane);
	const {statistics, sequenceFlows, agents} = useInstanceDiagramData(processInstance, hasDiagram);
	const {data: waitStates} = useProcessInstanceWaitStateStatistics(processInstance);
	const isRunning = isInstanceRunning(processInstance);
	const overlaysData = useInstanceDiagramOverlays({
		statistics: statistics.data,
		waitStates: isRunning ? waitStates : undefined,
		agents: isRunning ? agents.data : undefined,
		businessObjects: diagram?.businessObjects,
		processDefinitionId: processInstance.processDefinitionId,
		isModificationModeEnabled,
		isExecutionCountVisible,
		modificationBadges,
	});
	const selectableElements = useMemo(() => statistics.data?.map(({elementId}) => elementId) ?? [], [statistics.data]);
	const selectedElementIds = useMemo(() => (selectedElementId ? [selectedElementId] : undefined), [selectedElementId]);
	const highlightedElementIds = useMemo(
		() => statistics.data?.filter(({completed}) => completed > 0).map(({elementId}) => elementId),
		[statistics.data],
	);
	const highlightedSequenceFlows = useMemo(() => {
		const completedElements = new Set(highlightedElementIds);
		const compensationAssociations = Object.values(diagram?.diagramModel?.elementsById ?? {})
			.filter(
				({$type, targetRef}) =>
					$type === 'bpmn:Association' && targetRef?.isForCompensation && completedElements.has(targetRef.id),
			)
			.map(({id}) => id);
		return [...new Set([...(sequenceFlows.data?.map(({elementId}) => elementId) ?? []), ...compensationAssociations])];
	}, [diagram?.diagramModel, highlightedElementIds, sequenceFlows.data]);
	const customElementClasses = useMemo<[string, string][]>(() => {
		if (isModificationModeEnabled || !diagram) {
			return [];
		}
		const drilldownElements: [string, string][] = Object.entries(diagram.businessObjects)
			.filter(([, businessObject]) => ['bpmn:CallActivity', 'bpmn:BusinessRuleTask'].includes(businessObject.$type))
			.map(([elementId]) => [elementId, 'op-drilldown']);
		if (pendingDrillDownElementId) {
			drilldownElements.push([pendingDrillDownElementId, 'op-drilldown-loading']);
		}
		return drilldownElements;
	}, [diagram, isModificationModeEnabled, pendingDrillDownElementId]);

	const clearSelection = useCallback(
		(shouldClear?: (elementId?: string) => boolean) => {
			drillDownVersion.current++;
			setPendingDrillDownElementId(null);
			void navigate({
				to: '.',
				search: (current) => {
					if (shouldClear && !shouldClear(current.anchorElementId ?? current.elementId)) {
						return current;
					}
					return {
						...current,
						elementId: undefined,
						elementInstanceKey: undefined,
						anchorElementId: undefined,
						isMultiInstanceBody: undefined,
						isPlaceholder: undefined,
					};
				},
				replace: true,
			});
		},
		[navigate],
	);

	useEffect(() => {
		currentInstanceId.current = processInstanceId;
		if (previousInstanceId.current !== processInstanceId) {
			clearSelection();
		}
		previousInstanceId.current = processInstanceId;
		return () => {
			currentInstanceId.current = null;
		};
	}, [clearSelection, processInstanceId]);

	const selectElement = (elementId: string, isMultiInstanceBody?: boolean) => {
		drillDownVersion.current++;
		setPendingDrillDownElementId(null);
		void navigate({
			to: '.',
			search: (current) => ({
				...current,
				elementId,
				elementInstanceKey: undefined,
				anchorElementId: undefined,
				isMultiInstanceBody: isMultiInstanceBody || undefined,
				isPlaceholder: undefined,
			}),
			replace: true,
		});
	};

	const handleDrillDown = async (elementId: string) => {
		if (isModificationModeEnabled || pendingDrillDownElementId !== null) {
			return;
		}
		const elementType = diagram?.businessObjects[elementId]?.$type;
		if (elementType !== 'bpmn:CallActivity' && elementType !== 'bpmn:BusinessRuleTask') {
			return;
		}
		const version = ++drillDownVersion.current;
		const isCurrentDrillDown = () => {
			if (currentInstanceId.current !== processInstanceId || drillDownVersion.current !== version) {
				return false;
			}
			const currentSearch = processInstanceSearchSchema.parse(router.state.location.search);
			return (
				(currentSearch.anchorElementId ?? currentSearch.elementId) === selectedElementId &&
				currentSearch.elementInstanceKey === selection.elementInstanceKey
			);
		};
		setPendingDrillDownElementId(elementId);
		try {
			const element = await queryClient.fetchQuery(elementDrilldownQuery(processInstanceId, elementId));
			if (!isCurrentDrillDown()) {
				return;
			}
			const elementInstance = element.items[0];
			if (element.page.totalItems !== 1 || !elementInstance) {
				return;
			}
			const elementInstanceKey = elementInstance.elementInstanceKey;
			if (elementType === 'bpmn:CallActivity') {
				const called = await queryClient.fetchQuery(calledProcessQuery(elementInstanceKey));
				if (!isCurrentDrillDown()) {
					return;
				}
				const calledInstance = called.items[0];
				if (called.page.totalItems === 1 && calledInstance) {
					await navigate({
						to: '/operate/processes/$processInstanceId/details',
						params: {processInstanceId: calledInstance.processInstanceKey},
						search: {},
					});
				}
			} else {
				const called = await queryClient.fetchQuery(calledDecisionQuery(elementInstanceKey));
				if (!isCurrentDrillDown()) {
					return;
				}
				const calledDecision = called.items[0];
				if (called.page.totalItems === 1 && calledDecision) {
					await navigate({
						to: '/operate/decisions/$decisionInstanceId',
						params: {decisionInstanceId: calledDecision.decisionEvaluationInstanceKey},
					});
				}
			}
		} catch {
			if (!isCurrentDrillDown()) {
				return;
			}
			notificationsStore.displayNotification({
				kind: 'error',
				title: t(
					elementType === 'bpmn:CallActivity'
						? 'operate.processInstance.diagram.calledProcessError'
						: 'operate.processInstance.diagram.calledDecisionError',
				),
				isDismissable: true,
			});
		} finally {
			if (isCurrentDrillDown()) {
				setPendingDrillDownElementId(null);
			}
		}
	};

	const status =
		isXmlPending || (isXmlFetching && diagram === undefined)
			? 'loading'
			: xmlError instanceof ForbiddenError
				? 'forbidden'
				: isXmlError
					? 'error'
					: !hasDiagram
						? 'empty'
						: 'content';

	return (
		<Panel aria-label={t('operate.processInstance.diagram.panelLabel')}>
			<DiagramShell status={status} emptyMessage={{message: t('operate.processInstance.diagram.noDiagram')}}>
				{diagram && hasDiagram && (
					<Diagram
						key={processInstanceId}
						xml={diagram.xml}
						download={{
							xml: diagram.xml,
							filename: `${kebabCase(processInstance.processDefinitionName ?? processInstance.processDefinitionId)}_v${processInstance.processDefinitionVersion}.bpmn`,
						}}
						selectableElements={isModificationModeEnabled ? (modifiableElements ?? []) : selectableElements}
						selectedElementIds={selectedElementIds}
						onElementSelection={(elementId, isMultiInstance, clickedElementId) => {
							if (isModificationModeEnabled) {
								onModificationElementSelection?.(elementId, isMultiInstance);
								return;
							}
							if (elementId) {
								selectElement(elementId, isMultiInstance);
							} else if (clickedElementId && selectedElementIds?.includes(clickedElementId)) {
								selectElement(
									clickedElementId,
									diagram.businessObjects[clickedElementId]?.loopCharacteristics?.$type ===
										'bpmn:MultiInstanceLoopCharacteristics',
								);
							} else {
								clearSelection();
							}
						}}
						onRootChange={(rootElementId, getSelectionRootId) => {
							clearSelection((elementId) => elementId !== undefined && rootElementId !== getSelectionRootId(elementId));
						}}
						overlaysData={overlaysData}
						selectedElementOverlay={isModificationModeEnabled ? selectedElementOverlay : undefined}
						highlightedSequenceFlows={highlightedSequenceFlows}
						highlightedElementIds={highlightedElementIds}
						nonSelectableNodeTooltipText={
							isModificationModeEnabled ? t('operate.processInstance.diagram.notModifiable') : undefined
						}
						hasOuterBorderOnSelection={
							!isModificationModeEnabled ||
							(!search.elementInstanceKey &&
								(statistics.data?.find(({elementId}) => elementId === selection.elementId)?.active ?? 0) > 1)
						}
						customElementClasses={customElementClasses}
						onElementDoubleClick={(elementId) => void handleDrillDown(elementId)}
					>
						<InstanceDiagramOverlays />
					</Diagram>
				)}
			</DiagramShell>
		</Panel>
	);
}

export {InstanceDiagram};
