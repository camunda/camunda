/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createPortal} from 'react-dom';
import {useTranslation} from 'react-i18next';
import {observer} from 'mobx-react-lite';
import {CheckmarkOutline, Error, RadioButtonChecked, WarningFilled} from '@carbon/react/icons';
import type {ProcessDefinitionStatistic, DecisionInstanceState} from '@camunda/camunda-api-zod-schemas/8.11';
import {themeStore} from '#/shared/theme/theme';
import {Container} from './styled';

type ElementState = keyof Omit<ProcessDefinitionStatistic, 'elementId'>;

const STATE_LABEL_KEYS: Record<string, string> = {
	active: 'operate.shared.stateOverlay.active',
	incidents: 'operate.shared.stateOverlay.incidents',
	canceled: 'operate.shared.stateOverlay.canceled',
	completed: 'operate.shared.stateOverlay.completed',
	completedEndEvents: 'operate.shared.stateOverlay.completed',
	EVALUATED: 'operate.shared.stateOverlay.evaluated',
	FAILED: 'operate.shared.stateOverlay.failed',
};

type Props = {
	state: (ElementState | 'completedEndEvents') | DecisionInstanceState;
	container: HTMLElement;
	count?: number;
	isFaded?: boolean;
	testId?: string;
	title?: string;
};

const StateOverlay: React.FC<Props> = observer(
	({state, container, count, isFaded = false, testId = `state-overlay-${state}`, title}) => {
		const {t} = useTranslation();
		const showStatistic = count !== undefined;
		const labelKey = STATE_LABEL_KEYS[state];
		const label = title ?? (labelKey === undefined ? state : t(labelKey));

		return createPortal(
			<Container
				data-testid={testId}
				title={title}
				role="img"
				aria-label={showStatistic ? `${label}: ${count}` : label}
				$theme={themeStore.actualTheme}
				$state={state}
				$isFaded={isFaded}
				orientation="horizontal"
				gap={3}
				$showStatistic={showStatistic}
			>
				{['FAILED', 'incidents'].includes(state) && <WarningFilled />}
				{state === 'active' && <RadioButtonChecked />}
				{['EVALUATED', 'completedEndEvents'].includes(state) && <CheckmarkOutline />}
				{state === 'canceled' && <Error />}
				{showStatistic && <span>{count}</span>}
			</Container>,
			container,
		);
	},
);

export {StateOverlay};
export type {ElementState};
