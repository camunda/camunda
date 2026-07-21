/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Field} from 'react-final-form';
import {useTranslation} from 'react-i18next';
import {MultiSelect} from '@carbon/react';
import {spaceAndCapitalize} from '#/operate/shared/utils/spaceAndCapitalize';

type Props = {
	name: string;
	titleText: string;
	items: string[];
};

const FilterMultiSelect: React.FC<Props> = ({name, titleText, items}) => {
	const {t} = useTranslation();

	return (
		<Field name={name}>
			{({input}) => {
				const selectedItems: string[] = Array.isArray(input.value)
					? input.value
					: typeof input.value === 'string' && input.value
						? input.value.split(',')
						: [];

				return (
					<MultiSelect
						id={name}
						data-testid={name}
						items={items}
						selectedItems={selectedItems}
						itemToString={(selectedItem) => spaceAndCapitalize(selectedItem)}
						label={t('operate.shared.filterMultiSelect.chooseOption')}
						useTitleInItem={false}
						titleText={titleText}
						onChange={({selectedItems}) => {
							input.onChange(selectedItems?.length ? selectedItems : undefined);
						}}
						size="sm"
					/>
				);
			}}
		</Field>
	);
};

export {FilterMultiSelect};
