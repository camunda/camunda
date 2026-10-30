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
import {CircleAlert, CircleCheck, CircleDot, CircleX} from '@camunda/design-system/icons';
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

type Props = {
	state: OverlayState;
	container: HTMLElement;
	count?: number;
	isFaded?: boolean;
	testId?: string;
	title?: string;
};

const StateOverlay: React.FC<Props> = ({
	state,
	container,
	count,
	isFaded = false,
	testId = `state-overlay-${state}`,
	title,
}) => {
	const {t} = useTranslation();
	const showStatistic = count !== undefined;
	const labelKey = STATE_LABEL_KEYS[state];
	const label = title ?? (labelKey === undefined ? state : t(labelKey));

	return createPortal(
		<Badge
			data-testid={testId}
			title={title}
			role="img"
			aria-label={showStatistic ? `${label}: ${count}` : label}
			variant={STATE_VARIANTS[state] ?? 'neutral'}
			className={cn(showStatistic && '-translate-x-1/2', isFaded && 'opacity-60')}
		>
			{(state === 'FAILED' || state === 'incidents') && <CircleAlert aria-hidden />}
			{state === 'active' && <CircleDot aria-hidden />}
			{(state === 'EVALUATED' || state === 'completedEndEvents') && <CircleCheck aria-hidden />}
			{state === 'canceled' && <CircleX aria-hidden />}
			{showStatistic && <span>{count}</span>}
		</Badge>,
		container,
	);
};

export {StateOverlay};
