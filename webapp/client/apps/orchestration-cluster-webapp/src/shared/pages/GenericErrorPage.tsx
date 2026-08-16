/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Layer, Stack, Button as CarbonButton} from '@carbon/react';
import {Button, EmptyState} from '@camunda/design-system';
import {Compass} from 'lucide-react';
import {useTranslation} from 'react-i18next';
import SvgErrorRobot from '#/shared/svg/ErrorRobot';
import {featureFlags} from '#/shared/feature-flags';
import {cn} from '#/shared/cn';
import styles from './GenericErrorPage.module.scss';

type Props = {
	reset: () => void;
};

const GenericErrorPage: React.FC<Props> = ({reset}) => {
	const {t} = useTranslation();

	// DS-only: the DS's own EmptyState component instead of the hand-rolled
	// Layer/Stack/illustration markup below, which stays Carbon-only. The
	// illustration is dropped for a lucide icon, per the "drop the
	// illustration, EmptyState takes a node icon" precedent from
	// TasklistProcessesPage.tsx.
	if (featureFlags.dsTasklistUI) {
		return (
			<div className={cn(styles.page, styles.pageDS)}>
				<EmptyState
					icon={<Compass aria-hidden />}
					heading={t('errorGenericErrorPageTitle')}
					description={t('errorGenericErrorPageMessage')}
					action={<Button onClick={reset}>{t('errorGenericErrorPageButtonLabel')}</Button>}
				/>
			</div>
		);
	}

	return (
		<div className={styles.page}>
			<Layer withBackground className={styles.card}>
				<Stack orientation="horizontal" gap={6}>
					<SvgErrorRobot aria-hidden />
					<Stack gap={6}>
						<Stack gap={3}>
							<h1 className={styles.heading}>{t('errorGenericErrorPageTitle')}</h1>
							<p className={styles.description}>{t('errorGenericErrorPageMessage')}</p>
						</Stack>
						<CarbonButton onClick={reset}>{t('errorGenericErrorPageButtonLabel')}</CarbonButton>
					</Stack>
				</Stack>
			</Layer>
		</div>
	);
};

export {GenericErrorPage};
