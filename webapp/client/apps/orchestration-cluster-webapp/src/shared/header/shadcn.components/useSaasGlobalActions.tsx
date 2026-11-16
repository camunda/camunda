/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo} from 'react';
import {useTranslation} from 'react-i18next';
import {SaasNotifications} from '@camunda/oc-saas-notifications';
import {getBootConfig} from '#/shared/config/getBootConfig';

function useSaasGlobalActions(initialSaasToken: string | null) {
	const {t, i18n} = useTranslation();
	const {organizationId, clusterId} = getBootConfig();
	const isSaas = organizationId !== null && clusterId !== null && initialSaasToken !== null;
	const globalActions = useMemo(
		() =>
			isSaas
				? [
						{
							key: 'notifications',
							label: t('headerNotificationsLabel'),
							element: (
								<SaasNotifications
									locale={i18n.resolvedLanguage}
									labels={{
										title: t('headerNotificationsLabel'),
										loading: t('headerNotificationsLoading'),
										empty: t('headerNotificationsEmptyDescription'),
									}}
								/>
							),
						},
					]
				: undefined,
		[i18n.resolvedLanguage, isSaas, t],
	);

	return {isSaas, globalActions};
}

export {useSaasGlobalActions};
