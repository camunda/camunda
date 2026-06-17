/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useSuspenseQuery} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {queries} from '#/shared/http/queries';
import {tracking} from '#/shared/tracking';
import {EmptyState} from '#/operate/components/EmptyState/EmptyState';
import emptyStateIconUrl from '#/operate/assets/empty-state-process-instances-by-name.svg';

const NoInstancesEmptyState: React.FC = () => {
	const {t} = useTranslation();
	const {data: currentUser} = useSuspenseQuery(queries.getCurrentUser());
	const modelerLink = currentUser?.c8Links?.['modeler'];

	return (
		<EmptyState
			icon={<img src={emptyStateIconUrl} alt={t('operate.dashboard.noInstancesHeading')} />}
			heading={t('operate.dashboard.noInstancesHeading')}
			description={t('operate.dashboard.noInstancesDescription')}
			link={{
				label: t('operate.dashboard.learnMoreLink'),
				href: 'https://docs.camunda.io/docs/components/operate/operate-introduction/',
				onClick: () =>
					tracking.track({
						eventName: 'operate:dashboard-link-clicked',
						link: 'operate-docs',
					}),
			}}
			button={
				modelerLink !== undefined
					? {
							label: t('operate.dashboard.goToModelerButton'),
							href: modelerLink,
							onClick: () =>
								tracking.track({
									eventName: 'operate:dashboard-link-clicked',
									link: 'modeler',
								}),
						}
					: undefined
			}
		/>
	);
};

export {NoInstancesEmptyState};
