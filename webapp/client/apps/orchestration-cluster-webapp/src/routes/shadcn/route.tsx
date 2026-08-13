/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {C4Provider} from '@camunda/design-system';
import '@camunda/design-system/styles.css';
import {createFileRoute, Outlet} from '@tanstack/react-router';

const Route = createFileRoute('/shadcn')({
	component: RouteComponent,
});

function RouteComponent() {
	return (
		<C4Provider>
			<Outlet />
		</C4Provider>
	);
}

export {Route};
