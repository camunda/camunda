/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Heading} from '@camunda/design-system';
import {useTranslation} from 'react-i18next';

const AdminTenantsPage: React.FC = () => {
	const {t} = useTranslation();

	return (
		<Heading as="h1" variant="heading-lg">
			{t('admin.headerNavItemTenants')}
		</Heading>
	);
};

export {AdminTenantsPage};
