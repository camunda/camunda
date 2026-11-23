/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {OPERATE_BASE} from '#/shared/operateBase';
import {useTranslation} from 'react-i18next';
import {useNavigate} from '@tanstack/react-router';
import {Button} from '@camunda/design-system';
import {Pencil, Plus} from '@camunda/design-system/icons';
import {isValueRequired, type VariableCondition} from '../../VariablesFilter/variableConditions';
import {useVariableConditions} from '../../VariablesFilter/variableFilterStore';
import {truncate} from '../../VariablesFilter/truncate';
import {VariableFilterWarnings} from './VariableFilterWarnings';

const VariablesFilter: React.FC = () => {
	const {t} = useTranslation();
	const navigate = useNavigate();
	const conditions = useVariableConditions();

	const getConditionLabel = ({name, operator, value}: VariableCondition) => {
		const label = `${name} ${t(`operate.processes.variableFilter.operators.${operator}`)}`;
		return isValueRequired(operator) ? `${label} ${truncate(value)}` : label;
	};

	return (
		<div className="flex flex-col items-start gap-2">
			{conditions.length > 0 && (
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
			)}
			<VariableFilterWarnings conditions={conditions} />
			<Button
				variant="ghost"
				size="sm"
				onClick={() =>
					void navigate({
						to: `${OPERATE_BASE}/processes/filters/variables` as const,
						search: true,
						state: {operateVariableFilterOpenedFromList: true},
					})
				}
			>
				{conditions.length > 0 ? <Pencil aria-hidden /> : <Plus aria-hidden />}
				{t(
					conditions.length > 0
						? 'operate.processes.variableFilter.editConditions'
						: 'operate.processes.variableFilter.addCondition',
				)}
			</Button>
		</div>
	);
};

export {VariablesFilter};
