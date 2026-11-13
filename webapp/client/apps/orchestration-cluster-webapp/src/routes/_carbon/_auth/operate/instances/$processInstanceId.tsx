/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createFileRoute, redirect} from '@tanstack/react-router';
import {migrateLegacyProcessSearch} from '#/operate/shared/utils/migrateLegacyProcessSearch';

const Route = createFileRoute('/_carbon/_auth/operate/instances/$processInstanceId')({
	beforeLoad: ({location, params}) => {
		throw redirect({
			to: '/operate/processes/$processInstanceId',
			params,
			search: migrateLegacyProcessSearch(location) ?? location.search,
			hash: location.hash,
			replace: true,
		});
	},
});

export {Route};
