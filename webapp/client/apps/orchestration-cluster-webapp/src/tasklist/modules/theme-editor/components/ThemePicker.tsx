/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Button, Popover, PopoverContent, PopoverTrigger, Text} from '@camunda/design-system';
import {Check} from '@camunda/design-system/icons';
import {useState} from 'react';
import {cn} from '#/shared/cn';
import type {PresetOption} from '#/tasklist/modules/theme-editor/presets';

type Props<Value extends string> = {
	label: string;
	/** A value that is not one of the options counts as custom, for example a hex color. */
	value: string;
	options: PresetOption<Value>[];
	customLabel: string;
	onSelect: (value: Value) => void;
	onSelectCustom: () => void;
	/** Called while an option is hovered or focused, to preview it without committing. */
	onPreview: (value: Value) => void;
	onPreviewEnd: () => void;
};

const CUSTOM_SWATCH = 'conic-gradient(red, yellow, lime, aqua, blue, magenta, red)';

const Swatch: React.FC<{swatch: string}> = ({swatch}) => (
	<span aria-hidden className="size-4 shrink-0 rounded-full ring-1 ring-border" style={{background: swatch}} />
);

const OPTION_CLASS_NAME = cn(
	'flex items-center gap-2 rounded-md px-2 py-1.5 text-left text-sm outline-none',
	'hover:bg-neutral-background-medium focus-visible:bg-neutral-background-medium',
	'focus-visible:ring-2 focus-visible:ring-ring',
);

function ThemePicker<Value extends string>({
	label,
	value,
	options,
	customLabel,
	onSelect,
	onSelectCustom,
	onPreview,
	onPreviewEnd,
}: Props<Value>) {
	const [isOpen, setIsOpen] = useState(false);
	const preset = options.find((option) => option.value === value);
	const isCustom = preset === undefined;
	const selected = preset ?? {value, label: customLabel, swatch: value};

	return (
		<Popover
			open={isOpen}
			onOpenChange={(open) => {
				setIsOpen(open);

				if (!open) {
					onPreviewEnd();
				}
			}}
		>
			<PopoverTrigger asChild>
				<Button type="button" variant="secondary" className="h-auto w-full justify-between gap-3 px-3 py-2 text-left">
					<span className="flex min-w-0 flex-col">
						<Text as="span" variant="helper" className="text-neutral-foreground-subtle">
							{label}
						</Text>
						<Text as="span" variant="label-md-strong" className="truncate">
							{selected.label}
						</Text>
					</span>
					<Swatch swatch={selected.swatch} />
				</Button>
			</PopoverTrigger>
			<PopoverContent
				side="right"
				align="start"
				className="max-h-96 w-56 overflow-y-auto p-1"
				onMouseLeave={onPreviewEnd}
			>
				<div role="radiogroup" aria-label={label} className="flex flex-col">
					<button
						type="button"
						role="radio"
						aria-checked={isCustom}
						className={OPTION_CLASS_NAME}
						onMouseEnter={onPreviewEnd}
						onFocus={onPreviewEnd}
						onClick={() => {
							onSelectCustom();
							setIsOpen(false);
						}}
					>
						<Swatch swatch={isCustom ? selected.swatch : CUSTOM_SWATCH} />
						<span className="flex-1 truncate">{customLabel}</span>
						{isCustom ? <Check aria-hidden className="size-4" /> : null}
					</button>
					<div role="separator" className="my-1 h-px bg-border" />
					{options.map((option) => {
						const isSelected = option.value === value;

						return (
							<button
								key={option.value}
								type="button"
								role="radio"
								aria-checked={isSelected}
								className={OPTION_CLASS_NAME}
								onMouseEnter={() => onPreview(option.value)}
								onFocus={() => onPreview(option.value)}
								onClick={() => {
									onSelect(option.value);
									setIsOpen(false);
								}}
							>
								<Swatch swatch={option.swatch} />
								<span className="flex-1 truncate">{option.label}</span>
								{isSelected ? <Check aria-hidden className="size-4" /> : null}
							</button>
						);
					})}
				</div>
			</PopoverContent>
		</Popover>
	);
}

export {ThemePicker};
