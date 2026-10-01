/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createFileRoute} from '@tanstack/react-router';
import {Decisions} from '#/operate/pages/Decisions/shadcn.components/Decisions';
import {validateDecisionsSearch} from '#/operate/pages/Decisions/decisionsSearch';

export const Route = createFileRoute('/_shadcn/_auth/operate-preview/decisions/')({
	validateSearch: validateDecisionsSearch,
	// Migration scaffold only: this DS leaf reuses the Carbon search contract but skips
	// data prefetching until the shared decisions foundation components are ported.
	component: Decisions,
});
