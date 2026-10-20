/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Field} from 'react-final-form';
import {useTranslation} from 'react-i18next';
import {Label, MultiSelect, type MultiSelectOption} from '@camunda/design-system';
import {spaceAndCapitalize} from '#/operate/shared/utils/spaceAndCapitalize';

type Props = {
	name: string;
	titleText: string;
	items: string[];
};

const FilterMultiSelect: React.FC<Props> = ({name, titleText, items}) => {
	const {t} = useTranslation();
	const labelId = `${name}-label`;

	const options: MultiSelectOption[] = items.map((item) => ({
		value: item,
		label: spaceAndCapitalize(item),
	}));

	return (
		<Field name={name}>
			{({input}) => {
				const selectedItems: string[] = Array.isArray(input.value)
					? input.value
					: typeof input.value === 'string' && input.value
						? input.value.split(',')
						: [];

				return (
					<div>
						<Label id={labelId} htmlFor={name}>
							{titleText}
						</Label>
						<MultiSelect
							id={name}
							className="mt-1.5"
							options={options}
							value={selectedItems}
							placeholder={t('operate.shared.filterMultiSelect.chooseOption')}
							aria-labelledby={labelId}
							onValueChange={(value) => {
								input.onChange(value.length ? value : undefined);
							}}
							size="sm"
						/>
					</div>
				);
			}}
		</Field>
	);
};

export {FilterMultiSelect};
