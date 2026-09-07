/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ShieldAlert} from 'lucide-react';
import {useTranslation} from 'react-i18next';
import {PageEmptyState} from './PageEmptyState';

const ForbiddenPage: React.FC = () => {
	const {t} = useTranslation();

	return (
		<PageEmptyState
			icon={<ShieldAlert aria-hidden />}
			heading={t('forbiddenPageTitle')}
			description={t('forbiddenPageDesc')}
		/>
	);
};

export {ForbiddenPage};
