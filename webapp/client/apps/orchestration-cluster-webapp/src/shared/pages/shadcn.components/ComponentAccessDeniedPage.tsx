/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Button} from '@camunda/design-system';
import {ExternalLink, ShieldAlert} from 'lucide-react';
import {Trans, useTranslation} from 'react-i18next';
import {PageEmptyState} from './PageEmptyState';

const ComponentAccessDeniedPage: React.FC = () => {
	const {t} = useTranslation();

	return (
		<PageEmptyState
			icon={<ShieldAlert aria-hidden />}
			heading={t('componentAccessDeniedPageTitle')}
			description={<Trans i18nKey="componentAccessDeniedPageDesc" components={{strong: <strong />}} />}
			action={
				<Button asChild variant="link">
					<a
						href="https://docs.camunda.io/docs/next/components/concepts/access-control/authorizations/"
						target="_blank"
						rel="noopener noreferrer"
					>
						{t('componentAccessDeniedPageLinkLabel')}
						<ExternalLink aria-hidden />
					</a>
				</Button>
			}
		/>
	);
};

export {ComponentAccessDeniedPage};
