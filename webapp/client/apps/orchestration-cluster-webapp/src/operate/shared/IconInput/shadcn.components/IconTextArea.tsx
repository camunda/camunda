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
	...props
}) => {
	const generatedId = React.useId();
	const resolvedId = id ?? generatedId;

	return (
		<div className="flex flex-col gap-1.5">
			{labelText === undefined ? null : <Label htmlFor={resolvedId}>{labelText}</Label>}
			<div className="relative">
				<Textarea
					id={resolvedId}
					aria-invalid={invalid}
					className={cn(invalid ? 'pr-[69px]' : 'pr-[45px]', className)}
					{...props}
				/>
				<div className={cn('absolute top-[26px] right-[13px]', invalid && 'right-10')}>
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
		</div>
	);
};

export {IconTextArea};
