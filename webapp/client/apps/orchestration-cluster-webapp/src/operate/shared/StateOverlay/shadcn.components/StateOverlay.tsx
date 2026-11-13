/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

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

const VARIANT_CLASS_NAMES = {
	success:
		'border-transparent bg-[var(--success-foreground-subtle)] text-[var(--background)] [--pulse:var(--success-foreground-subtle)]',
	danger:
		'border-transparent bg-[var(--danger-foreground-subtle)] text-[var(--background)] [--pulse:var(--danger-foreground-subtle)]',
	neutral:
		'border-transparent bg-[var(--neutral-foreground-subtle)] text-[var(--background)] [--pulse:var(--neutral-foreground-subtle)]',
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
	const showStatistic = count !== undefined;
	const labelKey = STATE_LABEL_KEYS[state];
	const label = title ?? (labelKey === undefined ? state : t(labelKey));
	const fullCount = showStatistic ? new Intl.NumberFormat(i18n.resolvedLanguage).format(count) : undefined;
	const compactCount = showStatistic
		? new Intl.NumberFormat(i18n.resolvedLanguage, {notation: 'compact', maximumFractionDigits: 1}).format(count)
		: undefined;
	const baseLabel = showStatistic ? `${label}: ${fullCount}` : label;
	const statisticLabel = isFaded ? `${baseLabel}, ${t('operate.shared.stateOverlay.pending')}` : baseLabel;
	const isCornerAnchored = showStatistic && state !== 'completedEndEvents';
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
				showStatistic ? 'gap-1.5 pl-1 pr-2 -translate-x-1/2' : 'px-1',
				isCornerAnchored && '-translate-y-1/2',
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
