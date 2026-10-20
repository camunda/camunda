/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import * as React from 'react';
import {Textarea, IconButton, Label} from '@camunda/design-system';
import {cn} from '#/shared/cn';

interface Props extends Omit<React.ComponentProps<typeof Textarea>, 'aria-invalid'> {
	Icon: React.ComponentType<React.SVGProps<SVGSVGElement>>;
	invalid?: boolean;
	onIconClick: () => void;
	buttonLabel: string;
	labelText?: React.ReactNode;
	tooltipSide?: React.ComponentProps<typeof IconButton>['tooltipSide'];
}

const IconTextArea: React.FC<Props> = ({
	Icon,
	invalid,
	onIconClick,
	buttonLabel,
	labelText,
	tooltipSide = 'bottom',
	id,
	className,
	invalidText,
	helperText,
	...props
}) => {
	const generatedId = React.useId();
	const resolvedId = id ?? generatedId;
	const showInvalidText = invalid && invalidText !== undefined;
	const showHelperText = !invalid && helperText !== undefined;
	const descriptionId = `${resolvedId}-description`;

	return (
		<div className="flex flex-col gap-1.5">
			{labelText === undefined ? null : <Label htmlFor={resolvedId}>{labelText}</Label>}
			{/* Icon sits relative to the textarea only, never the error/helper text below it,
			    and anchors to the top padding corner (matching SearchInput's right-2 inset)
			    since the field grows with content rather than staying a fixed single-row height. */}
			<div className="relative">
				{/* @camunda/design-system's Textarea hardcodes `resize-none`; there's no prop to
				    opt back into native resize. The top-right icon here would collide with a
				    resize handle, so flag this as a DS gap if/when a resizable variant is needed. */}
				<Textarea
					id={resolvedId}
					aria-invalid={invalid}
					aria-describedby={showInvalidText || showHelperText ? descriptionId : undefined}
					className={cn(invalid ? 'pr-10' : 'pr-9', className)}
					{...props}
				/>
				<div className="absolute right-2 top-2">
					<IconButton
						variant="ghost"
						size="sm"
						icon={Icon}
						label={buttonLabel}
						tooltipSide={tooltipSide}
						onClick={onIconClick}
					/>
				</div>
			</div>
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

export {IconTextArea};
