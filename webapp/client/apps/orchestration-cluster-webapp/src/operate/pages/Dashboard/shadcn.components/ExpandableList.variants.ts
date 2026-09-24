/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {ExpandableListVariantComponent} from './ExpandableList.types';
import {ComposedCell} from './variants/ComposedCell';

/**
 * How a Dashboard list tile renders its rows.
 *
 * The row shape is still under design review, so it is a component axis rather
 * than a branch: adding a candidate is one module under `variants/` plus one
 * entry here, and choosing the winner is a one-line change to
 * `DEFAULT_EXPANDABLE_LIST_VARIANT`. Consumers do not pass `variant`, so no call
 * site changes when the decision lands. Exploratory candidates live on the
 * `operate-ds-expandable-variants` branch; only the adopted default belongs here.
 *
 * At cutover, drop the losing variants, this registry and the `variant` prop.
 */
const EXPANDABLE_LIST_VARIANTS = {
	composed: ComposedCell,
} satisfies Record<string, ExpandableListVariantComponent>;

type ExpandableListVariant = keyof typeof EXPANDABLE_LIST_VARIANTS;

const DEFAULT_EXPANDABLE_LIST_VARIANT: ExpandableListVariant = 'composed';

const EXPANDABLE_LIST_VARIANT_IDS = Object.keys(EXPANDABLE_LIST_VARIANTS) as ExpandableListVariant[];

export {EXPANDABLE_LIST_VARIANTS, EXPANDABLE_LIST_VARIANT_IDS, DEFAULT_EXPANDABLE_LIST_VARIANT};
export type {ExpandableListVariant};
