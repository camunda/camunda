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
	value: Value;
	options: PresetOption<Value>[];
	variant?: 'color' | 'radius';
	onSelect: (value: Value) => void;
	/** Called while an option is hovered or focused, to preview it without committing. */
	onPreview: (value: Value) => void;
	onPreviewEnd: () => void;
};

const Swatch: React.FC<{swatch: string; variant: 'color' | 'radius'}> = ({swatch, variant}) =>
	variant === 'color' ? (
		<span aria-hidden className="size-4 shrink-0 rounded-full ring-1 ring-border" style={{backgroundColor: swatch}} />
	) : (
		<span
			aria-hidden
			className="size-4 shrink-0 border-t-2 border-l-2 border-foreground"
			style={{borderTopLeftRadius: swatch === '0' ? 0 : `calc(${swatch} * 1.5)`}}
		/>
	);

function ThemePicker<Value extends string>({
	label,
	value,
	options,
	variant = 'color',
	onSelect,
	onPreview,
	onPreviewEnd,
}: Props<Value>) {
	const [isOpen, setIsOpen] = useState(false);
	const selected = options.find((option) => option.value === value) ?? options[0]!;

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
				<Button type="button" variant="ghost" className="h-auto w-full justify-between gap-3 px-3 py-2 text-left">
					<span className="flex min-w-0 flex-col">
						<Text as="span" variant="helper" className="text-neutral-foreground-subtle">
							{label}
						</Text>
						<Text as="span" variant="label-md-strong" className="truncate">
							{selected.label}
						</Text>
					</span>
					<Swatch swatch={selected.swatch} variant={variant} />
				</Button>
			</PopoverTrigger>
			<PopoverContent
				side="right"
				align="start"
				className="max-h-96 w-56 overflow-y-auto p-1"
				onMouseLeave={onPreviewEnd}
			>
				<div role="radiogroup" aria-label={label} className="flex flex-col">
					{options.map((option) => {
						const isSelected = option.value === value;

						return (
							<button
								key={option.value}
								type="button"
								role="radio"
								aria-checked={isSelected}
								className={cn(
									'flex items-center gap-2 rounded-md px-2 py-1.5 text-left text-sm outline-none',
									'hover:bg-neutral-background-medium focus-visible:bg-neutral-background-medium',
									'focus-visible:ring-2 focus-visible:ring-ring',
								)}
								onMouseEnter={() => onPreview(option.value)}
								onFocus={() => onPreview(option.value)}
								onClick={() => {
									onSelect(option.value);
									setIsOpen(false);
								}}
							>
								<Swatch swatch={option.swatch} variant={variant} />
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
