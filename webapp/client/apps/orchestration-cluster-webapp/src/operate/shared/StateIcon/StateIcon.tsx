/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {WarningFilled, CheckmarkOutline, RadioButtonChecked, PauseOutlineFilled} from './styled';
import {type CarbonIconType, Error, UnknownFilled} from '@carbon/react/icons';
import type {DecisionInstanceState, ProcessInstanceState} from '@camunda/camunda-api-zod-schemas/8.11';

const stateIconsMap = {
	FAILED: WarningFilled,
	INCIDENT: WarningFilled,
	ACTIVE: RadioButtonChecked,
	COMPLETED: CheckmarkOutline,
	EVALUATED: CheckmarkOutline,
	SUSPENDED: PauseOutlineFilled,
	TERMINATED: Error,
	UNSPECIFIED: UnknownFilled,
	UNKNOWN: UnknownFilled,
} as const satisfies Record<Props['state'], unknown>;

const stateLabelKeys = {
	FAILED: 'operate.shared.stateIcon.failed',
	INCIDENT: 'operate.shared.stateIcon.incident',
	ACTIVE: 'operate.shared.stateIcon.active',
	COMPLETED: 'operate.shared.stateIcon.completed',
	EVALUATED: 'operate.shared.stateIcon.evaluated',
	SUSPENDED: 'operate.shared.stateIcon.suspended',
	TERMINATED: 'operate.shared.stateIcon.terminated',
	UNSPECIFIED: 'operate.shared.stateIcon.unspecified',
	UNKNOWN: 'operate.shared.stateIcon.unknown',
} as const satisfies Record<Props['state'], string>;

type Props = {
	state: ProcessInstanceState | DecisionInstanceState | 'INCIDENT';
	size: React.ComponentProps<CarbonIconType>['size'];
};

const StateIcon: React.FC<Props> = ({state, ...props}) => {
	const {t} = useTranslation();
	const TargetComponent = stateIconsMap[state];
	return <TargetComponent role="img" aria-label={t(stateLabelKeys[state])} data-testid={`${state}-icon`} {...props} />;
};

export {StateIcon};
