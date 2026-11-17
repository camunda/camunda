/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {C3LicenseTag, useC3Profile} from '@camunda/camunda-composite-components';
import {EnvironmentBadge} from '@camunda/design-system';
import type {License} from '@camunda/camunda-api-zod-schemas/8.11';
import {getBootConfig} from '#/shared/config/getBootConfig';

const HeaderBadges: React.FC<{license: License}> = ({license}) => {
	const {clusters} = useC3Profile();
	const {clusterId} = getBootConfig();
	const stage = clusters?.find(({uuid}) => uuid === clusterId)?.labels?.camunda?.[0] ?? '';

	return (
		<div className="flex items-center gap-2">
			<EnvironmentBadge stage={stage} />
			{license.licenseType === 'saas' ? null : (
				<div className="operate-nav-v2-license-tag">
					<C3LicenseTag
						isProductionLicense={license.validLicense}
						isCommercial={license.isCommercial}
						expiresAt={license.expiresAt ?? undefined}
					/>
				</div>
			)}
		</div>
	);
};

export {HeaderBadges};
