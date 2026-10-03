/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo, useState} from 'react';
import {useTranslation} from 'react-i18next';
import type {TFunction} from 'i18next';
import {Field, type FieldInputProps} from 'react-final-form';
import {Label, Select, SelectContent, SelectItem, SelectTrigger, SelectValue} from '@camunda/design-system';
import {TextInputField} from '#/operate/shared/TextInputField/shadcn.components/TextInputField';
import {
	encodeFilterOperation,
	splitEncodedFilterOperation,
	type AdvancedStringFilterOperator,
} from '#/operate/shared/utils/advancedStringFilter';

const getOperatorConfig = (
	t: TFunction,
): Record<AdvancedStringFilterOperator, {label: string; placeholder?: string}> => ({
	$eq: {label: t('operate.shared.advancedStringFilter.operatorEquals')},
	$neq: {label: t('operate.shared.advancedStringFilter.operatorNotEquals')},
	$like: {label: t('operate.shared.advancedStringFilter.operatorContains')},
	$in: {
		label: t('operate.shared.advancedStringFilter.operatorIsOneOf'),
		placeholder: t('operate.shared.advancedStringFilter.operatorListPlaceholder'),
	},
	$notIn: {
		label: t('operate.shared.advancedStringFilter.operatorIsNotOneOf'),
		placeholder: t('operate.shared.advancedStringFilter.operatorListPlaceholder'),
	},
	$exists: {
		label: t('operate.shared.advancedStringFilter.operatorExists'),
		placeholder: t('operate.shared.advancedStringFilter.operatorExistsPlaceholder'),
	},
});

type Props = {
	name: string;
	label: string;
	selectableOperators: AdvancedStringFilterOperator[];
};

const AdvancedStringFilter: React.FC<Props> = ({name, label, selectableOperators}) => {
	return (
		<Field<string | undefined> name={name}>
			{({input}) => <AdvancedStringFilterField input={input} label={label} selectableOperators={selectableOperators} />}
		</Field>
	);
};

type FieldProps = {
	input: FieldInputProps<string | undefined, HTMLElement>;
	label: string;
	selectableOperators: AdvancedStringFilterOperator[];
};

const AdvancedStringFilterField: React.FC<FieldProps> = ({input, label, selectableOperators}) => {
	const {t} = useTranslation();
	const operatorConfig = useMemo(() => getOperatorConfig(t), [t]);
	const filter = splitEncodedFilterOperation(input.value ?? '');

	const [fallbackOperator, setFallbackOperator] = useState<AdvancedStringFilterOperator>('$eq');
	const selectedOperator = filter?.operator ?? fallbackOperator;

	const handleOperatorChange = (newOperator: AdvancedStringFilterOperator) => {
		setFallbackOperator(newOperator);
		if (filter?.value) {
			input.onChange(encodeFilterOperation(newOperator, filter.value));
		}
	};

	const handleValueChange = (newValue: string) => {
		if (newValue === '') {
			setFallbackOperator(selectedOperator);
			input.onChange(undefined);
			return;
		}
		input.onChange(encodeFilterOperation(selectedOperator, newValue));
	};

	return (
		<div className="grid grid-cols-[max-content_1fr] items-center gap-x-3">
			<Label htmlFor={input.name} className="col-span-2">
				{label}
			</Label>
			<Select
				value={selectedOperator}
				onValueChange={(newOperator) => handleOperatorChange(newOperator as AdvancedStringFilterOperator)}
			>
				<SelectTrigger
					id={`${input.name}.operator`}
					size="sm"
					aria-label={t('operate.shared.advancedStringFilter.operatorTypeLabel', {label})}
				>
					<SelectValue />
				</SelectTrigger>
				<SelectContent>
					{selectableOperators.map((operator) => (
						<SelectItem key={operator} value={operator}>
							{operatorConfig[operator].label}
						</SelectItem>
					))}
				</SelectContent>
			</Select>
			<TextInputField
				name={input.name}
				id={input.name}
				placeholder={operatorConfig[selectedOperator].placeholder}
				value={filter?.value ?? ''}
				onChange={(event) => handleValueChange(event.target.value)}
				onBlur={input.onBlur}
			/>
		</div>
	);
};

export {AdvancedStringFilter};
