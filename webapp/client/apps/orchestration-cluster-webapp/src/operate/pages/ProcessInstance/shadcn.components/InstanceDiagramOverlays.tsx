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
import {Text} from '@camunda/design-system';
import type {AgentInstance} from '@camunda/camunda-api-zod-schemas/8.11';
import type {BusinessObjects} from 'bpmn-js/lib/NavigatedViewer';
import {DiagramOverlayContext} from '#/operate/shared/Diagram/DiagramOverlayContext';
import type {OverlayEntry} from '#/operate/shared/Diagram/overlayTypes';
import {StateOverlay} from '#/operate/shared/StateOverlay/shadcn.components/StateOverlay';
import {getWaitStateLabel} from '#/operate/shared/utils/waitStates';
import {cn} from '#/shared/cn';

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
			shine.style.width = `${Number(shape.getAttribute('width') ?? 0) + 2}px`;
			shine.style.height = `${Number(shape.getAttribute('height') ?? 0) + 2}px`;
			shine.style.borderRadius = `${Number(shape.getAttribute('rx') ?? 0) + 1}px`;
		}
	}, [container, elementId]);

	return createPortal(
		<span
			ref={shineRef}
			className="instance-agent-shine pointer-events-none absolute -inset-px hidden p-0.5 opacity-70"
		/>,
		container,
	);
};

const InstanceDiagramOverlays: React.FC<Props> = ({businessObjects}) => {
	const overlays = useContext(DiagramOverlayContext);
	const {t} = useTranslation();

	return overlays.map(({type, payload, container, elementId}) => {
		if (type === 'instance-state') {
			const {elementState, count} = payload as StatePayload;
			return (
				<StateOverlay
					key={`${elementId}-${elementState}`}
					state={elementState}
					count={count}
					container={container}
					title={elementState === 'completed' ? t('operate.processInstance.diagram.executionCount') : undefined}
					elementName={businessObjects?.[elementId]?.name}
				/>
			);
		}
		if (type === 'instance-waiting') {
			const {waitingCount, centered} = payload as WaitingPayload;
			return createPortal(
				<Text
					as="span"
					variant="label-md-strong"
					className={cn(
						'inline-flex items-center rounded-full bg-warning-background-strong px-3 py-1 whitespace-nowrap text-warning-foreground-strong tabular-nums',
						centered && '-translate-x-1/2',
					)}
				>
					{getWaitStateLabel(waitingCount)}
				</Text>,
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
					className="inline-block rounded-full bg-background px-2 py-0.5 whitespace-nowrap text-primary-action-default shadow-sm"
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
		return null;
	});
};

export {InstanceDiagramOverlays};
