/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Tooltip} from '@carbon/react';
import {TooltipTrigger} from './styled';

type Props = {
	label: string;
	children: React.ReactNode;
};

const DisabledActionTooltip: React.FC<Props> = ({label, children}) => (
	<Tooltip label={label} align="top-end" enterDelayMs={0} leaveDelayMs={0} className="cds--icon-tooltip">
		<TooltipTrigger tabIndex={0}>{children}</TooltipTrigger>
	</Tooltip>
);

export {DisabledActionTooltip};
