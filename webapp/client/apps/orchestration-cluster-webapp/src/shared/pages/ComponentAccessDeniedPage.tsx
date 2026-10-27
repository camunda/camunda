/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Launch} from '@carbon/react/icons';
import {Link, Stack} from '@carbon/react';
import {Trans, useTranslation} from 'react-i18next';
import SvgForbidden from '#/shared/svg/Forbidden';
import styles from './ComponentAccessDeniedPage.module.scss';

const ComponentAccessDeniedPage: React.FC = () => {
	const {t} = useTranslation();

	return (
		<div className={styles.page}>
			<Stack gap={6} className={styles.content}>
				<SvgForbidden aria-hidden />
				<Stack gap={3}>
					<h1 className={styles.heading}>{t('componentAccessDeniedPageTitle')}</h1>
					<p className={styles.description}>
						<Trans i18nKey="componentAccessDeniedPageDesc" components={{strong: <strong />}} />
					</p>
				</Stack>
				<Link
					href="https://docs.camunda.io/docs/next/components/concepts/access-control/authorizations/"
					target="_blank"
					rel="noopener noreferrer"
					renderIcon={Launch}
				>
					{t('componentAccessDeniedPageLinkLabel')}
				</Link>
			</Stack>
		</div>
	);
};

export {ComponentAccessDeniedPage};
