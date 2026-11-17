/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Tooltip, TooltipContent, TooltipTrigger} from '@camunda/design-system';
import {cn} from '#/shared/cn';

type Props = {
	text: string;
	children?: React.ReactNode;
	className?: string;
};

const TruncatedText: React.FC<Props> = ({text, children = text, className}) => (
	<Tooltip>
		<TooltipTrigger asChild>
			<span tabIndex={0} className={cn('block overflow-hidden text-ellipsis whitespace-nowrap', className)}>
				{children}
			</span>
		</TooltipTrigger>
		<TooltipContent>{text}</TooltipContent>
	</Tooltip>
);

export {TruncatedText};
