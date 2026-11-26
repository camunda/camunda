/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useContext, useLayoutEffect, useRef} from 'react';
import {createPortal} from 'react-dom';
import {useTranslation} from 'react-i18next';
import {Badge, Text} from '@camunda/design-system';
import type {AgentInstance} from '@camunda/camunda-api-zod-schemas/8.11';
import type {BusinessObjects} from 'bpmn-js/lib/NavigatedViewer';
import {DiagramOverlayContext} from '#/operate/shared/Diagram/DiagramOverlayContext';
import type {OverlayEntry} from '#/operate/shared/Diagram/overlayTypes';
import {StateOverlay} from '#/operate/shared/StateOverlay/shadcn.components/StateOverlay';
import {getWaitStateLabel} from '#/operate/shared/utils/waitStates';
import {cn} from '#/shared/cn';
import {ModificationBadgeOverlay} from '#/operate/shared/ModificationBadgeOverlay/shadcn.components/ModificationBadgeOverlay';

type StatePayload = {elementState: React.ComponentProps<typeof StateOverlay>['state']; count?: number};
type WaitingPayload = {waitingCount: number; centered: boolean};
type AgentPayload = {
	agentInstanceKey: string;
	status: AgentInstance['status'];
	additionalActiveCount: number;
};

type Props = {
	businessObjects?: BusinessObjects;
};

function getOverlayElementName(businessObjects: BusinessObjects | undefined, elementId: string): string | undefined {
	const name = businessObjects?.[elementId]?.name?.trim();
	return name === undefined || name === '' ? undefined : name;
}

const AgentShineOverlay: React.FC<Pick<OverlayEntry, 'container' | 'elementId'>> = ({container, elementId}) => {
	const shineRef = useRef<HTMLSpanElement>(null);

	useLayoutEffect(() => {
		const shine = shineRef.current;
		if (!shine) {
			return;
		}
		const shape = container
			.closest('.djs-container')
			?.querySelector<SVGRectElement>(`[data-element-id="${CSS.escape(elementId)}"] .djs-visual rect`);
		shine.style.display = shape ? 'block' : 'none';
		if (shape) {
			const inset = (Number.parseFloat(getComputedStyle(shape).strokeWidth) || 0) / 2;
			shine.style.inset = `${inset}px`;
			shine.style.width = `${Number(shape.getAttribute('width') ?? 0) - inset * 2}px`;
			shine.style.height = `${Number(shape.getAttribute('height') ?? 0) - inset * 2}px`;
			shine.style.borderRadius = `${Math.max(Number(shape.getAttribute('rx') ?? 0) - inset, 0)}px`;
		}
	}, [container, elementId]);

	return createPortal(
		<span ref={shineRef} className="instance-agent-shine pointer-events-none absolute hidden p-0.5 opacity-70" />,
		container,
	);
};

const InstanceDiagramOverlays: React.FC<Props> = ({businessObjects}) => {
	const overlays = useContext(DiagramOverlayContext);
	const {t} = useTranslation();

	return overlays.map(({type, payload, container, elementId}) => {
		const elementName = getOverlayElementName(businessObjects, elementId);
		if (type === 'instance-state') {
			const {elementState, count} = payload as StatePayload;
			return (
				<StateOverlay
					key={`${elementId}-${elementState}`}
					state={elementState}
					count={count}
					container={container}
					title={elementState === 'completed' ? t('operate.processInstance.diagram.executionCount') : undefined}
					elementName={elementName}
				/>
			);
		}
		if (type === 'instance-waiting') {
			const {waitingCount, centered} = payload as WaitingPayload;
			const label = getWaitStateLabel(waitingCount);
			if (label === null) {
				return null;
			}
			const accessibleLabel = elementName === undefined ? label : `${elementName}, ${label}`;
			return createPortal(
				<Badge
					variant="warning"
					role="img"
					aria-label={accessibleLabel}
					className={cn('tabular-nums', centered && '-translate-x-1/2')}
				>
					{label}
				</Badge>,
				container,
				`waiting-${elementId}`,
			);
		}
		if (type === 'instance-agent-status') {
			const {status, additionalActiveCount, agentInstanceKey} = payload as AgentPayload;
			const labels: Partial<Record<AgentInstance['status'], string>> = {
				INITIALIZING: t('operate.processInstance.diagram.agentInitializing'),
				TOOL_DISCOVERY: t('operate.processInstance.diagram.agentDiscovering'),
				THINKING: t('operate.processInstance.diagram.agentThinking'),
				TOOL_CALLING: t('operate.processInstance.diagram.agentCalling'),
			};
			const label = labels[status];
			if (!label) {
				return null;
			}
			return createPortal(
				<Text
					as="span"
					variant="label-md-strong"
					className="inline-block rounded-full border border-accent-border-strong bg-background px-2 py-0.5 whitespace-nowrap text-accent-foreground-subtle shadow-sm"
				>
					{label}
					{additionalActiveCount > 0 && (
						<> {t('operate.processInstance.diagram.moreAgents', {count: additionalActiveCount})}</>
					)}
				</Text>,
				container,
				`${agentInstanceKey}-status`,
			);
		}
		if (type === 'instance-agent-shine') {
			const {agentInstanceKey} = payload as AgentPayload;
			return <AgentShineOverlay key={`${agentInstanceKey}-shine`} container={container} elementId={elementId} />;
		}
		if (type === 'instance-modification') {
			const {newTokenCount, cancelledTokenCount} = payload as {newTokenCount: number; cancelledTokenCount: number};
			return (
				<ModificationBadgeOverlay
					key={`modification-${elementId}`}
					container={container}
					newTokenCount={newTokenCount}
					cancelledTokenCount={cancelledTokenCount}
					elementName={elementName}
				/>
			);
		}
		return null;
	});
};

export {InstanceDiagramOverlays};
