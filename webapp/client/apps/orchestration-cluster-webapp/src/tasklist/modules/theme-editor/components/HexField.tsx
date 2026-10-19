/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Input} from '@camunda/design-system';
import {useState} from 'react';
import {normalizeHex, type HexColor} from '#/tasklist/modules/theme-editor/colorRamp';

type Props = {
	label: string;
	/** The color currently shown. A preset is shown as its representative color. */
	hex: HexColor;
	onChange: (hex: HexColor) => void;
};

const COMPLETE_HEX = /^#?[0-9a-f]{6}$/i;

const HexField: React.FC<Props> = ({label, hex, onChange}) => {
	// The text being typed. `null` follows the color from the parent, so the picker and presets stay in sync.
	const [typed, setTyped] = useState<string | null>(null);
	const draft = typed ?? hex;
	const isInvalid = typed !== null && normalizeHex(typed) === null;

	return (
		<div className="flex items-center gap-2 pb-1">
			<input
				type="color"
				aria-label={`${label} picker`}
				value={hex}
				onChange={(event) => {
					setTyped(null);
					onChange(event.target.value as HexColor);
				}}
				className="size-8 shrink-0 cursor-pointer overflow-hidden rounded-md border border-border bg-transparent p-0 [&::-moz-color-swatch]:border-0 [&::-webkit-color-swatch]:border-0 [&::-webkit-color-swatch-wrapper]:p-0"
			/>
			<Input
				aria-label={label}
				aria-invalid={isInvalid}
				value={draft}
				maxLength={7}
				spellCheck={false}
				className="font-mono"
				onChange={(event) => {
					setTyped(event.target.value);

					// Wait for all six digits, so typing `#abc` does not jump to `#aabbcc` mid-edit.
					if (COMPLETE_HEX.test(event.target.value)) {
						const normalized = normalizeHex(event.target.value);

						if (normalized !== null) {
							onChange(normalized);
						}
					}
				}}
				onBlur={() => {
					const normalized = normalizeHex(draft);

					if (normalized !== null) {
						onChange(normalized);
					}

					setTyped(null);
				}}
			/>
		</div>
	);
};

export {HexField};
