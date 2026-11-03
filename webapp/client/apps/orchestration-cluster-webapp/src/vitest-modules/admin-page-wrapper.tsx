/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {TooltipProvider, Toaster} from '@camunda/design-system';
import {createRouterContextWrapper} from './render-router-context';

function createAdminPageWrapper() {
	const RouterContextWrapper = createRouterContextWrapper();
	const queryClient = new QueryClient({defaultOptions: {queries: {retry: false}}});

	const Wrapper: React.FC<{children: React.ReactNode}> = ({children}) => (
		<QueryClientProvider client={queryClient}>
			<RouterContextWrapper>
				<TooltipProvider>{children}</TooltipProvider>
			</RouterContextWrapper>
			<Toaster />
		</QueryClientProvider>
	);

	return Wrapper;
}

export {createAdminPageWrapper};
