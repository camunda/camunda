/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {isHexColor, toHexColor, type HexColor} from '#/tasklist/modules/theme-editor/colorRamp';
import type {PresetOption} from '#/tasklist/modules/theme-editor/presets';
import {HexField} from './HexField';
import {ThemePicker} from './ThemePicker';

type Props<Value extends string> = {
	label: string;
	hexLabel: string;
	customLabel: string;
	value: Value | HexColor;
	options: PresetOption<Value>[];
	onSelect: (value: Value | HexColor) => void;
	onPreview: (value: Value) => void;
	onPreviewEnd: () => void;
};

/** The presets first. Choosing "Custom" starts from the current color and shows the hex field. */
function ColorField<Value extends string>({
	label,
	hexLabel,
	customLabel,
	value,
	options,
	onSelect,
	onPreview,
	onPreviewEnd,
}: Props<Value>) {
	const isCustom = isHexColor(value);

	return (
		<>
			<ThemePicker
				label={label}
				value={value}
				options={options}
				customLabel={customLabel}
				onSelect={onSelect}
				onSelectCustom={() => {
					if (!isCustom) {
						onSelect(toHexColor(options.find((option) => option.value === value)?.swatch ?? '') ?? '#000000');
					}
				}}
				onPreview={onPreview}
				onPreviewEnd={onPreviewEnd}
			/>
			{isCustom ? <HexField label={hexLabel} hex={value} onChange={onSelect} /> : null}
		</>
	);
}

export {ColorField};
