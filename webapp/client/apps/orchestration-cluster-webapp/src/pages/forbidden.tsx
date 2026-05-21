/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Layer, Stack} from '@carbon/react';
import {useTranslation} from 'react-i18next';
import SvgForbidden from '#/modules/svg/Forbidden';
import styles from './forbidden.module.scss';

const Forbidden: React.FC = () => {
	const {t} = useTranslation();

	return (
		<div className={styles['page']}>
			<Layer withBackground className={styles['card']}>
				<Stack orientation="horizontal" gap={6}>
					<SvgForbidden aria-hidden />
					<Stack gap={3}>
						<h1 className={styles['heading']}>{t('forbiddenPageTitle')}</h1>
						<p className={styles['description']}>{t('forbiddenPageDesc')}</p>
					</Stack>
				</Stack>
			</Layer>
		</div>
	);
};

export {Forbidden};
