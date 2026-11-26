/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, it} from 'vitest';
import {DEFAULT_DATE_TIME_RANGE_PRESETS} from '@camunda/design-system';
import {PRESET_LABELS} from './presetLabels';

describe('PRESET_LABELS', () => {
	it('should define a label for every design system preset id', () => {
		expect(Object.keys(PRESET_LABELS).sort()).toEqual(
			DEFAULT_DATE_TIME_RANGE_PRESETS.map((preset) => preset.id).sort(),
		);
	});
});
