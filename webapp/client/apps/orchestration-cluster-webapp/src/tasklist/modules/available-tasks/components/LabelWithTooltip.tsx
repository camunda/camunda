/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {cn} from '#/shared/cn';
import {TOOLTIP_DELAY_MS} from '#/tasklist/modules/available-tasks/tooltipDelay';
import {Tooltip, TooltipContent, TooltipTrigger} from '@camunda/design-system';

type Align = 'top-start' | 'top-end';

type Props = {
	screenReaderText: string;
	content: React.ReactNode;
	children: React.ReactNode;
	align: Align;
	className?: string;
};

const LabelWithTooltip: React.FC<Props> = ({screenReaderText, content, children, align, className}) => (
	<Tooltip delayDuration={TOOLTIP_DELAY_MS}>
		<TooltipTrigger asChild>
			<span
				className={cn(
					'inline-flex min-w-0 items-center gap-1 whitespace-nowrap text-xs text-neutral-foreground-strong',
					className,
				)}
			>
				<span className="sr-only">{screenReaderText}</span>
				<span className="contents" aria-hidden>
					{children}
				</span>
			</span>
		</TooltipTrigger>
		<TooltipContent side="top" align={align === 'top-end' ? 'end' : 'start'}>
			{content}
		</TooltipContent>
	</Tooltip>
);

export {LabelWithTooltip};
export type {Align};
