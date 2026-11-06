/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Tooltip, TooltipContent, TooltipTrigger} from '@camunda/design-system';

type Props = {
	label: string;
	content: React.ReactNode;
	children: React.ReactNode;
};

const IconTooltip: React.FC<Props> = ({label, content, children}) => (
	<Tooltip>
		<TooltipTrigger asChild>
			<button type="button" aria-label={label} className="inline-flex cursor-default items-center">
				{children}
			</button>
		</TooltipTrigger>
		<TooltipContent side="bottom" align="start">
			<div className="flex flex-col gap-1">{content}</div>
		</TooltipContent>
	</Tooltip>
);

export {IconTooltip};
