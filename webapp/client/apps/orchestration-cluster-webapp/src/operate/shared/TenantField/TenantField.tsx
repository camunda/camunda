/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Field} from 'react-final-form';
import {useTranslation} from 'react-i18next';
import {useSuspenseQuery} from '@tanstack/react-query';
import {Dropdown} from '@carbon/react';
import {queries} from '#/shared/http/queries';

type Props = {
	onChange?: (selectedItem: string) => void;
};

const TenantField: React.FC<Props> = ({onChange}) => {
	const {t} = useTranslation();
	const {data: tenants} = useSuspenseQuery({
		...queries.getCurrentUser(),
		select: ({tenants}) => tenants,
	});
	const tenantsById = Object.fromEntries(tenants.map(({tenantId, name}) => [tenantId, name]));
	const items = ['all', ...tenants.map(({tenantId}) => tenantId)];

	return (
		<Field name="tenantId">
			{({input}) => {
				return (
					<Dropdown
						label={t('operate.shared.tenantField.selectTenant')}
						aria-label={t('operate.shared.tenantField.selectTenant')}
						titleText={t('operate.shared.tenantField.tenant')}
						hideLabel
						id="tenantId"
						onChange={({selectedItem}) => {
							input.onChange(selectedItem);
							onChange?.(selectedItem);
						}}
						items={items}
						itemToString={(item: string) => {
							return item === 'all' ? t('operate.shared.tenantField.allTenants') : (tenantsById[item] ?? item);
						}}
						selectedItem={items.includes(input.value) ? input.value : ''}
						size="sm"
					/>
				);
			}}
		</Field>
	);
};

export {TenantField};
