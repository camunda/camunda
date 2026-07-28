/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
	CriticalIcon,
	SkillLevelAdvancedIcon,
	SkillLevelBasicIcon,
	SkillLevelIntermediateIcon,
} from '#/shared/design-system-compat';
import {featureFlags} from '#/shared/feature-flags';
import {LabelWithPopover, type Align} from './LabelWithPopover';
import {getPriorityLabel} from '#/tasklist/modules/available-tasks/getPriorityLabel';
import styles from './PriorityLabel.module.scss';

type PriorityLabelProps = {
	priority: number;
	align?: Align;
};

const ICON_MAPPINGS = {
	low: SkillLevelBasicIcon,
	medium: SkillLevelIntermediateIcon,
	high: SkillLevelAdvancedIcon,
	critical: CriticalIcon,
};

const PriorityLabel: React.FC<PriorityLabelProps> = ({priority, align = 'top-end'}) => {
	const priorityLabel = getPriorityLabel(priority);
	const PriorityIcon = ICON_MAPPINGS[priorityLabel.key];

	return (
		<LabelWithPopover
			title={priorityLabel.long}
			// Carbon's .cds--popover-content sets no font-size of its own (only
			// color/background — verified directly against its scss), so old-UI's
			// popover text relied entirely on this explicit type-style. Plain text
			// (no wrapper) is correct only for the DS Tooltip path, which sizes via
			// its own text-xs default.
			popoverContent={
				featureFlags.dsTasklistUI ? (
					priorityLabel.long
				) : (
					<span className={styles.popoverBody}>{priorityLabel.long}</span>
				)
			}
			align={align}
		>
			<PriorityIcon className={styles.inlineIcon} />
			{priorityLabel.short}
		</LabelWithPopover>
	);
};

export {PriorityLabel};
