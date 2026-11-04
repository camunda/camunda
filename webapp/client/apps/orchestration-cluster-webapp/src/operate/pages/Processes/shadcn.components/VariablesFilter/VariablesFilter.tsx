/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {useNavigate} from '@tanstack/react-router';
import {Button} from '@camunda/design-system';
import {Pencil} from '@camunda/design-system/icons';
import {isValueRequired, type VariableCondition} from '../../VariablesFilter/variableConditions';
import {useVariableConditions} from '../../VariablesFilter/variableFilterStore';
import {truncate} from '../../VariablesFilter/truncate';
import {SingleConditionFields} from './SingleConditionFields';

const VariablesFilter: React.FC = () => {
	const {t} = useTranslation();
	const navigate = useNavigate();
	const conditions = useVariableConditions();
	const showInlineForm = conditions.length === 0 || (conditions.length === 1 && conditions[0]?.operator === 'equals');

	const getConditionLabel = ({name, operator, value}: VariableCondition) => {
		const label = `${name} ${t(`operate.processes.variableFilter.operators.${operator}`)}`;
		return isValueRequired(operator) ? `${label} ${truncate(value)}` : label;
	};

	if (showInlineForm) {
		return <SingleConditionFields />;
	}

	return (
		<div className="flex flex-col items-start gap-2">
			<ul aria-label={t('operate.processes.variableFilter.activeConditions')} className="m-0 list-none p-0 text-sm">
				{conditions.map((condition, index) => (
					<li
						key={`${index}-${condition.name}-${condition.operator}-${condition.value}`}
						className="line-clamp-2 break-all"
					>
						{getConditionLabel(condition)}
					</li>
				))}
			</ul>
			<Button
				variant="ghost"
				size="sm"
				onClick={() =>
					void navigate({
						to: '/operate-preview/processes/filters/variables',
						search: true,
						state: {operateVariableFilterOpenedFromList: true},
					})
				}
			>
				<Pencil aria-hidden />
				{t('operate.processes.variableFilter.editConditions')}
			</Button>
		</div>
	);
};

export {VariablesFilter};
