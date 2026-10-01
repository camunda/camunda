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
import {Label, Select, SelectContent, SelectItem, SelectTrigger, SelectValue} from '@camunda/design-system';
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
	const selectTenantLabel = t('operate.shared.tenantField.selectTenant');

	return (
		<Field name="tenantId">
			{({input}) => {
				const selectedItem = items.includes(input.value) ? input.value : '';

				return (
					<>
						<Label htmlFor="tenantId" className="sr-only">
							{t('operate.shared.tenantField.tenant')}
						</Label>
						<Select
							value={selectedItem}
							onValueChange={(selectedValue) => {
								input.onChange(selectedValue);
								onChange?.(selectedValue);
							}}
						>
							<SelectTrigger id="tenantId" aria-label={selectTenantLabel}>
								<SelectValue placeholder={selectTenantLabel} />
							</SelectTrigger>
							<SelectContent>
								{items.map((item) => (
									<SelectItem key={item} value={item}>
										{item === 'all' ? t('operate.shared.tenantField.allTenants') : (tenantsById[item] ?? item)}
									</SelectItem>
								))}
							</SelectContent>
						</Select>
					</>
				);
			}}
		</Field>
	);
};

export {TenantField};
