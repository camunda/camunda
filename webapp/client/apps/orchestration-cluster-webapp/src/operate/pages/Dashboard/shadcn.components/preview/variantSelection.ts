/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
	EXPANDABLE_LIST_VARIANT_IDS,
	type ExpandableListVariant,
} from '#/operate/pages/Dashboard/shadcn.components/ExpandableList.variants';

/** `all` renders every registered variant against identical data, side by side. */
type VariantSelection = ExpandableListVariant | 'all';

/**
 * Derived from the registry, so a newly registered candidate appears in the
 * preview switcher without touching this file or the route.
 */
const VARIANT_SELECTIONS: VariantSelection[] = ['all', ...EXPANDABLE_LIST_VARIANT_IDS];

const parseVariantSelection = (value: unknown): VariantSelection =>
	VARIANT_SELECTIONS.includes(value as VariantSelection) ? (value as VariantSelection) : 'all';

export {VARIANT_SELECTIONS, parseVariantSelection};
export type {VariantSelection};
