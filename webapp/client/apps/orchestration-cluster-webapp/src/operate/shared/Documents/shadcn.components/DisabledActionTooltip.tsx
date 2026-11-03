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
	children: React.ReactNode;
};

const DisabledActionTooltip: React.FC<Props> = ({label, children}) => (
	<Tooltip>
		<TooltipTrigger asChild>
			<span tabIndex={0} className="inline-flex cursor-not-allowed [&_button]:pointer-events-none">
				{children}
			</span>
		</TooltipTrigger>
		<TooltipContent side="top">{label}</TooltipContent>
	</Tooltip>
);

export {DisabledActionTooltip};
