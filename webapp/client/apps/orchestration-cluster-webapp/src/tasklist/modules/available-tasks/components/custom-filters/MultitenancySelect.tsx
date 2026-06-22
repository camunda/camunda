/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Select, SelectItem} from '@carbon/react';
import {useSuspenseQuery} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {queries} from '#/shared/http/queries';

const MultitenancySelect: React.FC<React.ComponentProps<typeof Select>> = (props) => {
	const {t} = useTranslation();
	const {data: tenants} = useSuspenseQuery({
		...queries.getCurrentUser(),
		select: ({tenants}) => tenants,
	});

	return (
		<Select {...props} disabled={props.disabled}>
			<SelectItem value="" text={t('tasklist.customFiltersModalAllTenants')} />
			{tenants.map(({tenantId, name}) => (
				<SelectItem key={tenantId} value={tenantId} text={name} />
			))}
		</Select>
	);
};

export {MultitenancySelect};
