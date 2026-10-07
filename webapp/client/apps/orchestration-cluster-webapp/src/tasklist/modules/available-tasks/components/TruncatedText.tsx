/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useRef, useState} from 'react';
import {Text, Tooltip, TooltipContent, TooltipTrigger} from '@camunda/design-system';
import {cn} from '#/shared/cn';
import {TOOLTIP_DELAY_MS} from '#/tasklist/modules/available-tasks/tooltipDelay';

type Props = React.ComponentProps<typeof Text>;

/**
 * Single-line text that is clipped with an ellipsis and revealed in a tooltip on hover.
 * The tooltip only opens when the text is actually clipped. No focus stop is added,
 * because the surrounding task card is already a focusable link.
 */
const TruncatedText: React.FC<Props> = ({className, children, ...props}) => {
	const ref = useRef<HTMLElement>(null);
	const [isOpen, setIsOpen] = useState(false);

	const handleOpenChange = (open: boolean) => {
		const element = ref.current;
		setIsOpen(open && element !== null && element.scrollWidth > element.clientWidth + 1);
	};

	return (
		<Tooltip delayDuration={TOOLTIP_DELAY_MS} open={isOpen} onOpenChange={handleOpenChange}>
			<TooltipTrigger asChild>
				<Text ref={ref} className={cn('block min-w-0 truncate', className)} {...props}>
					{children}
				</Text>
			</TooltipTrigger>
			<TooltipContent side="top" align="start" className="max-w-xs break-words">
				{children}
			</TooltipContent>
		</Tooltip>
	);
};

export {TruncatedText};
