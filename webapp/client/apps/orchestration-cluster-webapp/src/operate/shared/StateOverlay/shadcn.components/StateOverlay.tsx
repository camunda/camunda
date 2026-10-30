/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useLayoutEffect} from 'react';
import {createPortal} from 'react-dom';
import {useTranslation} from 'react-i18next';
import {Badge} from '@camunda/design-system';
import {CircleAlert, CircleCheck, CircleX, Disc, TriangleAlert} from '@camunda/design-system/icons';
import type {ProcessDefinitionStatistic, DecisionInstanceState} from '@camunda/camunda-api-zod-schemas/8.11';
import {cn} from '#/shared/cn';

type ElementState = keyof Omit<ProcessDefinitionStatistic, 'elementId'>;

type OverlayState = (ElementState | 'completedEndEvents') | DecisionInstanceState;

const STATE_LABEL_KEYS: Record<string, string> = {
	active: 'operate.shared.stateOverlay.active',
	incidents: 'operate.shared.stateOverlay.incidents',
	canceled: 'operate.shared.stateOverlay.canceled',
	completed: 'operate.shared.stateOverlay.completed',
	completedEndEvents: 'operate.shared.stateOverlay.completed',
	EVALUATED: 'operate.shared.stateOverlay.evaluated',
	FAILED: 'operate.shared.stateOverlay.failed',
};

const STATE_VARIANTS: Partial<Record<OverlayState, 'success' | 'danger' | 'neutral'>> = {
	active: 'success',
	incidents: 'danger',
	FAILED: 'danger',
	completedEndEvents: 'neutral',
	EVALUATED: 'neutral',
	canceled: 'neutral',
};

// Pill backgrounds must stay solid: the default Badge variants are too faint against the diagram canvas.
const VARIANT_CLASS_NAMES = {
	success:
		'border-transparent bg-[var(--success-foreground-subtle)] text-[var(--background)] [--pulse:var(--success-foreground-subtle)]',
	danger:
		'border-transparent bg-[var(--danger-foreground-subtle)] text-[var(--background)] [--pulse:var(--danger-foreground-subtle)]',
	neutral:
		'border-transparent bg-[var(--neutral-foreground-subtle)] text-[var(--background)] [--pulse:var(--neutral-foreground-subtle)]',
};

// bpmn-js places the overlay with pixel offsets tuned for the larger legacy pill. Statistic pills anchored to a plain
// element corner are re-centred on that corner using their own size. Pills with a deliberate horizontal offset
// (such as completed end events) keep their position. The centred flag is set on the container instead of
// React state, so a re-created overlay never paints (or animates) an uncentred frame.
const centreOnCorner = (container: HTMLElement): void => {
	const overlay = container.parentElement;
	const elementId = overlay?.parentElement?.getAttribute('data-container-id');
	if (!overlay || !elementId) {
		return;
	}
	const visual = container.ownerDocument.querySelector<SVGGraphicsElement>(
		`[data-element-id="${elementId}"] .djs-visual`,
	);
	if (!visual) {
		return;
	}
	const {x, y, width, height} = visual.getBBox();
	const left = parseFloat(overlay.style.left);
	const top = parseFloat(overlay.style.top);
	const isLeftEdge = left === x;
	const isRightEdge = left === x + width;
	if (Number.isNaN(top) || (!isLeftEdge && !isRightEdge)) {
		return;
	}
	overlay.style.left = `${isLeftEdge ? x : x + width}px`;
	overlay.style.top = `${top > y + height / 2 ? y + height : y}px`;
	container.dataset.centred = 'true';
};

type Props = {
	state: OverlayState;
	container: HTMLElement;
	count?: number;
	isFaded?: boolean;
	testId?: string;
	title?: string;
	elementName?: string;
};

const StateOverlay: React.FC<Props> = ({
	state,
	container,
	count,
	isFaded = false,
	testId = `state-overlay-${state}`,
	title,
	elementName,
}) => {
	const {t, i18n} = useTranslation();
	useLayoutEffect(() => {
		if (count !== undefined) {
			centreOnCorner(container);
		}
	}, [container, count]);
	const showStatistic = count !== undefined;
	const labelKey = STATE_LABEL_KEYS[state];
	const label = title ?? (labelKey === undefined ? state : t(labelKey));
	const fullCount = showStatistic ? new Intl.NumberFormat(i18n.resolvedLanguage).format(count) : undefined;
	const compactCount = showStatistic
		? new Intl.NumberFormat(i18n.resolvedLanguage, {notation: 'compact', maximumFractionDigits: 1}).format(count)
		: undefined;
	const baseLabel = showStatistic ? `${label}: ${fullCount}` : label;
	const statisticLabel = isFaded ? `${baseLabel}, ${t('operate.shared.stateOverlay.pending')}` : baseLabel;
	const variant = STATE_VARIANTS[state] ?? 'neutral';
	const accessibleLabel = elementName === undefined ? statisticLabel : `${elementName}, ${statisticLabel}`;

	return createPortal(
		<Badge
			data-testid={testId}
			title={accessibleLabel}
			role="img"
			variant={variant}
			className={cn(
				'align-top',
				VARIANT_CLASS_NAMES[variant],
				showStatistic ? 'gap-1.5 pl-1 pr-2 -translate-x-1/2 in-data-[centred]:-translate-y-1/2' : 'px-1',
				isFaded && 'state-overlay-pending-ring overflow-visible',
			)}
		>
			{state === 'FAILED' && <TriangleAlert aria-hidden />}
			{state === 'incidents' && (
				<CircleAlert aria-hidden className="fill-[var(--background)] stroke-[var(--danger-foreground-subtle)]" />
			)}
			{state === 'active' && <Disc aria-hidden className="[&_circle:last-child]:fill-current" />}
			{(state === 'EVALUATED' || state === 'completed' || state === 'completedEndEvents') && (
				<CircleCheck aria-hidden />
			)}
			{state === 'canceled' && <CircleX aria-hidden />}
			{showStatistic && <span>{compactCount}</span>}
		</Badge>,
		container,
	);
};

export {StateOverlay};
