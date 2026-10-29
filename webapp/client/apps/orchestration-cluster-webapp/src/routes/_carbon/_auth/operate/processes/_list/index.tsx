/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createFileRoute, redirect} from '@tanstack/react-router';
import {withCanonicalProcessesSearch} from '#/operate/pages/Processes/processesFilter';
import {migrateLegacyProcessSearch} from '#/operate/shared/utils/migrateLegacyProcessSearch';

const Route = createFileRoute('/_carbon/_auth/operate/processes/_list/')({
	beforeLoad: ({location}) => {
		const migrated = migrateLegacyProcessSearch(location);
		if (migrated !== null) {
			throw redirect({
				to: '/operate/processes',
				search: withCanonicalProcessesSearch(migrated),
				hash: location.hash,
				replace: true,
			});
		}
	},
});

export {Route};
