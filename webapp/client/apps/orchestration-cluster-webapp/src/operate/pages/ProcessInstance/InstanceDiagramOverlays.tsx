/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useContext} from 'react';
import {createPortal} from 'react-dom';
import styled, {keyframes} from 'styled-components';
import {useTranslation} from 'react-i18next';
import type {AgentInstance} from '@camunda/camunda-api-zod-schemas/8.10';
import {DiagramOverlayContext} from '#/operate/shared/Diagram/DiagramOverlayContext';
import {StateOverlay, type ElementState} from '#/operate/shared/StateOverlay/StateOverlay';

const AgentTag = styled.span`
	display: inline-block;
	white-space: nowrap;
	padding: var(--cds-spacing-01) var(--cds-spacing-03);
	border-radius: 100px;
	background: var(--cds-layer-01);
	color: var(--cds-link-primary);
	box-shadow: 0 0 4px var(--cds-focus);
	font-weight: 600;
`;

const shine = keyframes`
	0% { background-position: 0% 0%; }
	50% { background-position: 100% 100%; }
	100% { background-position: 0% 0%; }
`;

const AgentShine = styled.span<{$width: number; $height: number; $radius: number}>`
	display: block;
	pointer-events: none;
	border: 2px solid var(--cds-focus);
	width: ${({$width}) => `${$width}px`};
	height: ${({$height}) => `${$height}px`};
	border-radius: ${({$radius}) => `${$radius}px`};
	opacity: 0.7;
	animation: ${shine} 8s linear infinite;
	background-image: linear-gradient(120deg, transparent, var(--cds-focus), transparent);
	background-size: 300% 300%;
`;

const WaitingTag = styled.span<{$centered: boolean}>`
	display: inline-block;
	white-space: nowrap;
	padding: var(--cds-spacing-01) var(--cds-spacing-03);
	background: var(--cds-support-warning);
	color: #000;
	border-radius: var(--cds-spacing-02);
	font-variant-numeric: tabular-nums;
	transform: ${({$centered}) => ($centered ? 'translateX(-50%)' : 'none')};
`;

const ModificationBadge = styled.span`
	display: inline-block;
	padding: var(--cds-spacing-01) var(--cds-spacing-03);
	border-radius: var(--cds-spacing-02);
	background: var(--cds-layer-01);
	color: var(--cds-text-primary);
	border: 1px solid var(--cds-border-subtle-01);
`;

type StatePayload = {elementState: ElementState | 'completedEndEvents'; count?: number};
type WaitingPayload = {label: string; centered: boolean};
type AgentPayload = {
	agentInstanceKey: string;
	status: AgentInstance['status'];
	additionalActiveCount: number;
};

function InstanceDiagramOverlays() {
	const overlays = useContext(DiagramOverlayContext);
	const {t} = useTranslation();

	return overlays.map(({type, payload, container, elementId}) => {
		if (type === 'instance-state') {
			const {elementState, count} = payload as StatePayload;
			return (
				<StateOverlay
					key={`${elementId}-${elementState}`}
					testId={`instance-state-${elementId}-${elementState}`}
					state={elementState}
					count={count}
					container={container}
					title={elementState === 'completed' ? t('operate.processInstance.diagram.executionCount') : undefined}
				/>
			);
		}
		if (type === 'instance-waiting') {
			const {label, centered} = payload as WaitingPayload;
			return createPortal(
				<WaitingTag key={elementId} $centered={centered} data-testid={`instance-waiting-${elementId}`}>
					{label}
				</WaitingTag>,
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
				<AgentTag data-testid={`instance-agent-${elementId}`}>
					{label}
					{additionalActiveCount > 0 && t('operate.processInstance.diagram.moreAgents', {count: additionalActiveCount})}
				</AgentTag>,
				container,
				`${agentInstanceKey}-status`,
			);
		}
		if (type === 'instance-agent-shine') {
			const {agentInstanceKey} = payload as AgentPayload;
			const shape = document.querySelector<SVGRectElement>(
				`[data-element-id="${CSS.escape(elementId)}"] .djs-visual rect`,
			);
			if (!shape) {
				return null;
			}
			return createPortal(
				<AgentShine
					$width={Number(shape.getAttribute('width') ?? 0)}
					$height={Number(shape.getAttribute('height') ?? 0)}
					$radius={Number(shape.getAttribute('rx') ?? 0)}
					data-testid={`instance-agent-shine-${elementId}`}
				/>,
				container,
				`${agentInstanceKey}-shine`,
			);
		}
		if (type === 'instance-modification') {
			const {newTokenCount, cancelledTokenCount} = payload as {newTokenCount: number; cancelledTokenCount: number};
			return createPortal(
				<ModificationBadge data-testid={`instance-modification-${elementId}`}>
					{newTokenCount > 0 && `+${newTokenCount}`}
					{cancelledTokenCount > 0 && ` −${cancelledTokenCount}`}
				</ModificationBadge>,
				container,
				`modification-${elementId}`,
			);
		}
		return null;
	});
}

export {InstanceDiagramOverlays};
