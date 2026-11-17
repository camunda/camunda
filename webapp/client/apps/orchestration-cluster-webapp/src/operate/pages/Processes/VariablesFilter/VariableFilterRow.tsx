/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {Dropdown, TextInput} from '@carbon/react';
import {Close, Maximize} from '@carbon/react/icons';
import {IconTextInput} from '#/operate/shared/IconInput/IconTextInput';
import {
	VARIABLE_OPERATORS,
	isValueRequired,
	type ConditionErrors,
	type VariableCondition,
	type VariableOperator,
} from './variableConditions';
import {DeleteButton, FilterRow, ValueFieldContainer} from './styled';

type Props = {
	id: string;
	condition: VariableCondition;
	errors: ConditionErrors;
	onChange: (changes: Partial<VariableCondition>) => void;
	onDelete: () => void;
	isDeleteHidden: boolean;
	onEditValue: () => void;
};

const VALUE_PLACEHOLDER_KEYS: Partial<Record<VariableOperator, string>> = {
	contains: 'operate.processes.variableFilter.containsPlaceholder',
	oneOf: 'operate.processes.variableFilter.oneOfPlaceholder',
};

const VariableFilterRow: React.FC<Props> = ({
	id,
	condition,
	errors,
	onChange,
	onDelete,
	isDeleteHidden,
	onEditValue,
}) => {
	const {t} = useTranslation();

	return (
		<FilterRow>
			<TextInput
				id={`${id}.name`}
				labelText={t('operate.processes.variableFilter.name')}
				hideLabel
				placeholder={t('operate.processes.variableFilter.namePlaceholder')}
				value={condition.name}
				onChange={(event) => onChange({name: event.target.value})}
				invalid={errors.name !== undefined}
				invalidText={errors.name}
				size="sm"
				autoComplete="off"
			/>
			<Dropdown<VariableOperator>
				id={`${id}.operator`}
				titleText={t('operate.processes.variableFilter.operator')}
				hideLabel
				label={t('operate.processes.variableFilter.selectOperator')}
				items={[...VARIABLE_OPERATORS]}
				itemToString={(operator) => (operator ? t(`operate.processes.variableFilter.operators.${operator}`) : '')}
				selectedItem={condition.operator}
				onChange={({selectedItem}) => {
					const operator = selectedItem ?? 'equals';
					onChange(isValueRequired(operator) ? {operator} : {operator, value: ''});
				}}
				size="sm"
				autoAlign
			/>
			<ValueFieldContainer>
				{isValueRequired(condition.operator) && (
					<IconTextInput
						id={`${id}.value`}
						labelText={t('operate.processes.variableFilter.value')}
						hideLabel
						placeholder={t(
							VALUE_PLACEHOLDER_KEYS[condition.operator] ?? 'operate.processes.variableFilter.valuePlaceholder',
						)}
						value={condition.value}
						onChange={(event) => onChange({value: event.target.value})}
						invalid={errors.value !== undefined}
						invalidText={errors.value}
						size="sm"
						Icon={Maximize}
						buttonLabel={t('operate.processes.variableFilter.openEditor')}
						onIconClick={onEditValue}
					/>
				)}
			</ValueFieldContainer>
			<DeleteButton
				kind="ghost"
				size="sm"
				label={t('operate.processes.variableFilter.removeCondition')}
				align="top-right"
				onClick={onDelete}
				$hidden={isDeleteHidden}
			>
				<Close />
			</DeleteButton>
		</FilterRow>
	);
};

export {VariableFilterRow};
