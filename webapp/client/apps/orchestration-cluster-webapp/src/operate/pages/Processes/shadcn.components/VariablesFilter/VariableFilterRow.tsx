/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {IconButton, Input, Select, SelectContent, SelectItem, SelectTrigger, SelectValue} from '@camunda/design-system';
import {Maximize2, X} from '@camunda/design-system/icons';
import {cn} from '#/shared/cn';
import {
	VARIABLE_OPERATORS,
	isValueRequired,
	type ConditionErrors,
	type VariableCondition,
	type VariableOperator,
} from '../../VariablesFilter/variableConditions';

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
		<div className="grid grid-cols-[1fr_170px_1fr_auto] items-start gap-4">
			<Input
				id={`${id}.name`}
				aria-label={t('operate.processes.variableFilter.name')}
				placeholder={t('operate.processes.variableFilter.namePlaceholder')}
				value={condition.name}
				onChange={(event) => onChange({name: event.target.value})}
				aria-invalid={errors.name !== undefined}
				invalidText={errors.name}
				size="sm"
				autoComplete="off"
			/>
			<Select
				value={condition.operator}
				onValueChange={(operator) => {
					const nextOperator = operator as VariableOperator;
					onChange(isValueRequired(nextOperator) ? {operator: nextOperator} : {operator: nextOperator, value: ''});
				}}
			>
				<SelectTrigger
					id={`${id}.operator`}
					size="sm"
					className="w-full"
					aria-label={t('operate.processes.variableFilter.operator')}
				>
					<SelectValue placeholder={t('operate.processes.variableFilter.selectOperator')} />
				</SelectTrigger>
				<SelectContent>
					{VARIABLE_OPERATORS.map((operator) => (
						<SelectItem key={operator} value={operator}>
							{t(`operate.processes.variableFilter.operators.${operator}`)}
						</SelectItem>
					))}
				</SelectContent>
			</Select>
			<div className="relative">
				{isValueRequired(condition.operator) && (
					<>
						<Input
							id={`${id}.value`}
							aria-label={t('operate.processes.variableFilter.value')}
							placeholder={t(
								VALUE_PLACEHOLDER_KEYS[condition.operator] ?? 'operate.processes.variableFilter.valuePlaceholder',
							)}
							value={condition.value}
							onChange={(event) => onChange({value: event.target.value})}
							aria-invalid={errors.value !== undefined}
							invalidText={errors.value}
							size="sm"
							className="pr-9"
							autoComplete="off"
						/>
						<IconButton
							type="button"
							variant="ghost"
							size="xs"
							icon={Maximize2}
							label={t('operate.processes.variableFilter.openEditor')}
							tooltipSide="top"
							onClick={onEditValue}
							className="absolute top-1 right-1"
						/>
					</>
				)}
			</div>
			<IconButton
				type="button"
				variant="ghost"
				size="sm"
				icon={X}
				label={t('operate.processes.variableFilter.removeCondition')}
				tooltipSide="top"
				onClick={onDelete}
				className={cn(isDeleteHidden && 'invisible')}
			/>
		</div>
	);
};

export {VariableFilterRow};
