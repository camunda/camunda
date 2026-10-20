/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Tooltip, TooltipContent, TooltipTrigger} from '@camunda/design-system';

type Align = 'top-start' | 'top-end';

type Props = {
	label: string;
	content: React.ReactNode;
	children: React.ReactNode;
	align: Align;
};

const LabelWithTooltip: React.FC<Props> = ({label, content, children, align}) => (
	<Tooltip>
		<TooltipTrigger asChild>
			<span className="inline-flex items-center text-xs text-neutral-foreground-strong">
				<span className="sr-only">{label}</span>
				<span className="inline-flex items-center gap-1" aria-hidden>
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
