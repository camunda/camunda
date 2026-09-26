/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {ExpandableListVariantComponent} from './ExpandableList.types';
import {ComposedCell} from './variants/ComposedCell';
import {NativeExpansionCell} from './variants/NativeExpansionCell';

const EXPANDABLE_LIST_VARIANTS = {
	composed: ComposedCell,
	nativeExpansion: NativeExpansionCell,
} satisfies Record<string, ExpandableListVariantComponent>;

type ExpandableListVariant = keyof typeof EXPANDABLE_LIST_VARIANTS;

const DEFAULT_EXPANDABLE_LIST_VARIANT: ExpandableListVariant = 'composed';

const EXPANDABLE_LIST_VARIANT_IDS = Object.keys(EXPANDABLE_LIST_VARIANTS) as ExpandableListVariant[];

export {EXPANDABLE_LIST_VARIANTS, EXPANDABLE_LIST_VARIANT_IDS, DEFAULT_EXPANDABLE_LIST_VARIANT};
export type {ExpandableListVariant};
