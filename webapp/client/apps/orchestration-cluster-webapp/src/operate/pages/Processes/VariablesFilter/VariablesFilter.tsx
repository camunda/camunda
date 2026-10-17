/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {useNavigate} from '@tanstack/react-router';
import {Button} from '@carbon/react';
import {Edit} from '@carbon/react/icons';
import {Title} from '#/operate/shared/FiltersPanel/styled';
import {isValueRequired, type VariableCondition} from './variableConditions';
import {useVariableConditions} from './variableFilterStore';
import {SingleConditionFields} from './SingleConditionFields';
import {ConditionItem, ConditionList} from './styled';
import {truncate} from './truncate';

const VariablesFilter: React.FC = () => {
	const {t} = useTranslation();
	const navigate = useNavigate();
	const conditions = useVariableConditions();
	const showInlineForm = conditions.length === 0 || (conditions.length === 1 && conditions[0]?.operator === 'equals');

	const getConditionLabel = ({name, operator, value}: VariableCondition) => {
		const label = `${name} ${t(`operate.processes.variableFilter.operators.${operator}`)}`;
		return isValueRequired(operator) ? `${label} ${truncate(value)}` : label;
	};

	return (
		<>
			<Title>{t('operate.processes.variableFilter.title')}</Title>
			{showInlineForm ? (
				<SingleConditionFields />
			) : (
				<>
					<ConditionList aria-label={t('operate.processes.variableFilter.activeConditions')}>
						{conditions.map((condition, index) => (
							<ConditionItem key={`${index}-${condition.name}-${condition.operator}-${condition.value}`}>
								{getConditionLabel(condition)}
							</ConditionItem>
						))}
					</ConditionList>
					<Button
						kind="ghost"
						size="sm"
						renderIcon={Edit}
						onClick={() =>
							void navigate({
								to: '/operate/processes/filters/variables',
								search: true,
								state: {operateVariableFilterOpenedFromList: true},
							})
						}
					>
						{t('operate.processes.variableFilter.editConditions')}
					</Button>
				</>
			)}
		</>
	);
};

export {VariablesFilter};
