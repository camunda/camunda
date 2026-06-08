/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {C3UserConfigurationProvider} from '@camunda/camunda-composite-components';
import {C3ThemePersister} from '#/shared/theme/C3ThemePersister';
import {getBootConfig} from '#/shared/config/getBootConfig';
import {getStage} from '#/shared/config/getStage';
import {fetchSaasToken} from '#/shared/c3/fetchSaasToken';

const STAGE = getStage(window.location.host);

type Props = {
	currentApp: 'tasklist' | 'operate' | 'admin' | undefined;
	initialSaasToken: string | null;
	children: React.ReactNode;
};

const C3Provider: React.FC<Props> = ({currentApp, initialSaasToken, children}) => {
	const {organizationId, clusterId} = getBootConfig();

	if (initialSaasToken === null || organizationId === null || clusterId === null) {
		return <>{children}</>;
	}

	return (
		<C3UserConfigurationProvider
			activeOrganizationId={organizationId}
			userToken={initialSaasToken}
			getNewUserToken={fetchSaasToken}
			currentClusterUuid={clusterId}
			currentApp={currentApp}
			stage={STAGE === 'unknown' ? 'dev' : STAGE}
			handleTheme
		>
			<C3ThemePersister />
			{children}
		</C3UserConfigurationProvider>
	);
};

export {C3Provider};
