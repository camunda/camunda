/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {lazy, Suspense, type ComponentProps} from 'react';
import {EditorStyles} from './editorStyles';

const LazyMonacoDiffEditor = lazy(async () => {
	const [{loadMonaco}, {MonacoDiffEditor}] = await Promise.all([
		import('#/shared/monaco/loadMonaco'),
		import('./MonacoDiffEditor'),
	]);
	loadMonaco();
	return {default: MonacoDiffEditor};
});

const DiffEditor = (props: ComponentProps<typeof LazyMonacoDiffEditor>) => (
	<>
		<EditorStyles />
		<Suspense>
			<LazyMonacoDiffEditor {...props} />
		</Suspense>
	</>
);

export {DiffEditor};
