/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useC3Profile} from '@camunda/camunda-composite-components';
import {EnvironmentBadge, useMediaQuery} from '@camunda/design-system';
import {useSuspenseQuery} from '@tanstack/react-query';
import {getBootConfig} from '#/shared/config/getBootConfig';
import {queries} from '#/shared/http/queries';
import {LicenseBadges} from './LicenseBadges';

const HeaderBadges: React.FC = () => {
	const {clusters} = useC3Profile();
	const {data: license} = useSuspenseQuery(queries.getLicense());
	const isBelowLg = useMediaQuery('(width < 64rem)');
	const {clusterId} = getBootConfig();
	const stage = clusters?.find(({uuid}) => uuid === clusterId)?.labels?.camunda?.[0] ?? '';

	return (
		<div className="flex items-center gap-2">
			<EnvironmentBadge stage={stage} />
			{isBelowLg ? null : <LicenseBadges license={license} />}
		</div>
	);
};

export {HeaderBadges};
