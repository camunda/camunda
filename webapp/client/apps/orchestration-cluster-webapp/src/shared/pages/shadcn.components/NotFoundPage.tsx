/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Button} from '@camunda/design-system';
import {Link} from '@tanstack/react-router';
import {FileQuestion} from 'lucide-react';
import {useTranslation} from 'react-i18next';
import {useActiveComponentHomeRoute} from '#/shared/useActiveComponentHomeRoute';
import {PageEmptyState} from './PageEmptyState';

const NotFoundPage: React.FC = () => {
	const {t} = useTranslation();
	const homeRoute = useActiveComponentHomeRoute() ?? '/';

	return (
		<PageEmptyState
			icon={<FileQuestion aria-hidden />}
			heading={t('notFoundPageTitle')}
			description={t('notFoundPageDescription')}
			action={
				<Button asChild>
					<Link to={homeRoute}>{t('notFoundPageButtonLabel')}</Link>
				</Button>
			}
		/>
	);
};

export {NotFoundPage};
