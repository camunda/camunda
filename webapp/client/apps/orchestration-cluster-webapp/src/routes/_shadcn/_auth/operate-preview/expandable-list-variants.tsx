/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createFileRoute} from '@tanstack/react-router';
import {ExpandableListVariantsPreview} from '#/operate/pages/Dashboard/shadcn.components/preview/ExpandableListVariantsPreview';
import {
	parseVariantSelection,
	type VariantSelection,
} from '#/operate/pages/Dashboard/shadcn.components/preview/variantSelection';

export const Route = createFileRoute('/_shadcn/_auth/operate-preview/expandable-list-variants')({
	validateSearch: (search: Record<string, unknown>): {variant: VariantSelection} => ({
		variant: parseVariantSelection(search.variant),
	}),
	component: ExpandableListVariantsPreview,
});
