/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {Alert} from '@camunda/design-system';
import type {VariableCondition} from '../../VariablesFilter/variableConditions';

const SOFT_WARNING_THRESHOLD = 8;

type Props = {
	conditions: VariableCondition[];
};

const VariableFilterWarnings: React.FC<Props> = ({conditions}) => {
	const {t} = useTranslation();

	return (
		<>
			{conditions.some(({operator}) => operator === 'contains') && (
				<Alert variant="warning" role="status" description={t('operate.processes.variableFilter.containsWarning')} />
			)}
			{conditions.length >= SOFT_WARNING_THRESHOLD && (
				<Alert variant="info" role="status" description={t('operate.processes.variableFilter.manyConditionsWarning')} />
			)}
		</>
	);
};

export {VariableFilterWarnings};
