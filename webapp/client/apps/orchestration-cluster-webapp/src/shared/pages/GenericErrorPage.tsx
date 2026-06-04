/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Layer, Stack, Button} from '@carbon/react';
import {useTranslation} from 'react-i18next';
import SvgErrorRobot from '#/shared/svg/ErrorRobot';
import styles from './GenericErrorPage.module.scss';

type Props = {reset: () => void};

const GenericErrorPage: React.FC<Props> = ({reset}) => {
	const {t} = useTranslation();

	return (
		<div className={styles['page']}>
			<Layer withBackground className={styles['card']}>
				<Stack orientation="horizontal" gap={6}>
					<SvgErrorRobot aria-hidden />
					<Stack gap={6}>
						<Stack gap={3}>
							<h1 className={styles['heading']}>{t('errorGenericErrorPageTitle')}</h1>
							<p className={styles['description']}>{t('errorGenericErrorPageMessage')}</p>
						</Stack>
						<Button onClick={reset}>{t('errorGenericErrorPageButtonLabel')}</Button>
					</Stack>
				</Stack>
			</Layer>
		</div>
	);
};

export {GenericErrorPage};
