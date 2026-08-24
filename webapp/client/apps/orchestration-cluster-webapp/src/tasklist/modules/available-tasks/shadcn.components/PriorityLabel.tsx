/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {CircleAlert, SignalHigh, SignalLow, SignalMedium} from 'lucide-react';
import {getPriorityLabel} from '#/tasklist/modules/available-tasks/getPriorityLabel';
import {LabelWithTooltip, type Align} from './LabelWithTooltip';

type Props = {
	priority: number;
	align?: Align;
};

const ICON_MAPPINGS = {
	low: SignalLow,
	medium: SignalMedium,
	high: SignalHigh,
	critical: CircleAlert,
};

const PriorityLabel: React.FC<Props> = ({priority, align = 'top-end'}) => {
	const priorityLabel = getPriorityLabel(priority);
	const PriorityIcon = ICON_MAPPINGS[priorityLabel.key];

	return (
		<LabelWithTooltip title={priorityLabel.long} content={priorityLabel.long} align={align}>
			<PriorityIcon className="size-4 shrink-0" aria-hidden />
			{priorityLabel.short}
		</LabelWithTooltip>
	);
};

export {PriorityLabel};
