/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {C4Provider, useResolvedTheme} from '@camunda/design-system';
import '@camunda/design-system/styles.css';
import {createFileRoute, Outlet} from '@tanstack/react-router';
import './index.scss';

const Route = createFileRoute('/shadcn')({
	component: RouteComponent,
});

function RouteComponent() {
	const theme = useResolvedTheme();

	return (
		<C4Provider theme={theme}>
			<Outlet />
		</C4Provider>
	);
}

export {Route};
