/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import * as React from 'react';
import {Button, Label} from '@camunda/design-system';
import {cn} from '#/shared/cn';

interface Props {
	Icon: React.ComponentType<React.SVGProps<SVGSVGElement>>;
	invalid?: boolean;
	invalidText?: React.ReactNode;
	helperText?: React.ReactNode;
	onIconClick: () => void;
	onClick?: () => void;
	buttonLabel: string;
	labelText?: React.ReactNode;
	id?: string;
	className?: string;
	value: string;
	placeholder?: string;
	title?: string;
	disabled?: boolean;
}

// This is a read-only, click-to-open trigger (date range modal), the exact role the
// design system's own `DateRangePicker` trigger fills for its popover. It's built on
// the same base (`Button variant="secondary"`) and styling (truncated label + trailing
// icon at opacity-60) rather than an editable `Input`, so it matches that convention
// exactly instead of approximating it piecemeal on top of a different primitive.
const IconTextInput: React.FC<Props> = ({
	Icon,
	invalid,
	invalidText,
	helperText,
	onIconClick,
	onClick,
	buttonLabel,
	labelText,
	id,
	className,
	value,
	placeholder,
	title,
	disabled,
}) => {
	const generatedId = React.useId();
	const resolvedId = id ?? generatedId;
	const labelId = `${resolvedId}-label`;
	const descriptionId = `${resolvedId}-description`;
	const showInvalidText = invalid && invalidText !== undefined;
	const showHelperText = !invalid && helperText !== undefined;

	return (
		<div className="flex flex-col gap-1.5">
			{labelText === undefined ? null : (
				<Label id={labelId} htmlFor={resolvedId}>
					{labelText}
				</Label>
			)}
			<Button
				id={resolvedId}
				type="button"
				variant="secondary"
				disabled={disabled}
				aria-invalid={invalid}
				// Mirrors `DateRangePicker`'s own `aria-label`/`aria-labelledby` contract:
				// the visible field label is the accessible name whenever one is given,
				// falling back to `buttonLabel` only when this is used label-less.
				aria-labelledby={labelText === undefined ? undefined : labelId}
				aria-label={labelText === undefined ? buttonLabel : undefined}
				aria-describedby={showInvalidText || showHelperText ? descriptionId : undefined}
				title={title}
				onClick={onClick ?? onIconClick}
				className={cn('w-full justify-between font-normal', !value && 'text-neutral-foreground-subtle', className)}
			>
				<span className="truncate">{value || placeholder}</span>
				<Icon className="size-4 shrink-0 opacity-60" aria-hidden="true" />
			</Button>
			{showInvalidText ? (
				<span id={descriptionId} className="text-xs text-danger-foreground-subtle">
					{invalidText}
				</span>
			) : null}
			{showHelperText ? (
				<span id={descriptionId} className="text-xs text-neutral-foreground-subtle">
					{helperText}
				</span>
			) : null}
		</div>
	);
};

export {IconTextInput};
