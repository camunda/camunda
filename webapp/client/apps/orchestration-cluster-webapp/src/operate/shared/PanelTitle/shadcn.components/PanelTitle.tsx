/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Heading} from '@camunda/design-system';
import {cn} from '#/shared/cn';

type Props = {
	isVertical?: boolean;
	className?: string;
	children?: React.ReactNode;
};

// Carbon's headingCompact01 (14px/18px, font-weight 600) maps exactly to the
// DS's heading-xs variant, so no weight override is needed here (unlike
// EmptyMessage/MetricPanel, whose Carbon tokens have a different weight).
const PanelTitle: React.FC<Props> = ({isVertical = false, className, children}) => {
	return (
		<Heading
			as="h2"
			variant="heading-xs"
			className={cn('m-0 text-foreground', isVertical && '-rotate-180 [writing-mode:vertical-lr]', className)}
		>
			{children}
		</Heading>
	);
};

export {PanelTitle};
