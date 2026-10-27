/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {lazy} from 'react';

const LazyMonacoEditor = lazy(async () => {
	const [{loadMonaco}, {MonacoEditor}] = await Promise.all([
		import('#/shared/monaco/loadMonaco'),
		import('./MonacoEditor'),
	]);
	loadMonaco();
	return {default: MonacoEditor};
});

export {LazyMonacoEditor};
