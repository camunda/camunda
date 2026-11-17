/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {Label, Select, SelectContent, SelectItem, SelectTrigger, SelectValue} from '@camunda/design-system';
import type {QueryTenantsRequestBody} from '@camunda/camunda-api-zod-schemas/8.11';
import {queries} from '#/shared/http/queries';

const TENANTS_REQUEST_BODY: QueryTenantsRequestBody = {sort: [{field: 'name', order: 'ASC'}], page: {limit: 100}};

type Props = {
	value: string | undefined;
	onChange: (tenantId: string) => void;
	errorMessage?: string;
};

const ClusterVariableTenantSelect: React.FC<Props> = ({value, onChange, errorMessage}) => {
	const {t} = useTranslation();
	const {data, isPending, isError} = useQuery(queries.queryTenants(TENANTS_REQUEST_BODY));
	const tenants = data?.items ?? [];

	return (
		<div className="flex flex-col gap-1.5">
			<Label htmlFor="clusterVariableTenantId">{t('admin.clusterVariables.tenantFieldLabel')}</Label>
			<Select value={tenants.some(({tenantId}) => tenantId === value) ? value : ''} onValueChange={onChange}>
				<SelectTrigger id="clusterVariableTenantId" className="w-full" aria-invalid={Boolean(errorMessage)}>
					<SelectValue
						placeholder={
							isPending
								? t('admin.clusterVariables.tenantLoadingPlaceholder')
								: t('admin.clusterVariables.tenantFieldPlaceholder')
						}
					/>
				</SelectTrigger>
				<SelectContent>
					{tenants.map((tenant) => (
						<SelectItem key={tenant.tenantId} value={tenant.tenantId}>
							{tenant.name}
						</SelectItem>
					))}
				</SelectContent>
			</Select>
			{isError ? (
				<span role="alert" className="text-xs text-danger-foreground-subtle">
					{t('admin.clusterVariables.tenantsLoadError')}
				</span>
			) : errorMessage ? (
				<span role="alert" className="text-xs text-danger-foreground-subtle">
					{errorMessage}
				</span>
			) : null}
		</div>
	);
};

export {ClusterVariableTenantSelect};
