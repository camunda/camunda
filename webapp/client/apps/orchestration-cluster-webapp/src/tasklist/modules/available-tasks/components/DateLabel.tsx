/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Stack} from '#/shared/design-system-compat';
import {featureFlags} from '#/shared/feature-flags';
import {formatISODateTime} from '#/tasklist/modules/dates/formatDateRelative';
import {LabelWithPopover, type Align} from './LabelWithPopover';
import styles from './DateLabel.module.scss';

const DateLabel: React.FC<{
	date: Exclude<ReturnType<typeof formatISODateTime>, null>;
	relativeLabel: string;
	absoluteLabel: string;
	icon?: React.ReactNode;
	align?: Align;
}> = ({date, relativeLabel, absoluteLabel, icon, align = 'top-start'}) => (
	<LabelWithPopover
		title={
			['week', 'months', 'years'].includes(date.relative.resolution)
				? `${absoluteLabel} ${date.relative.speech}`
				: `${relativeLabel} ${date.relative.speech}`
		}
		// Carbon's .cds--popover-content sets no font-size of its own (only
		// color/background), so old-UI's popover text relied entirely on these
		// explicit type-styles. Plain spans (no classNames) are correct only for
		// the DS Tooltip path, which sizes via its own text-xs default.
		popoverContent={
			<Stack orientation="vertical" gap={2}>
				<span className={featureFlags.dsTasklistUI ? undefined : styles.popoverHeading}>{absoluteLabel}</span>
				<span className={featureFlags.dsTasklistUI ? undefined : styles.popoverBody}>{date.absolute.text}</span>
			</Stack>
		}
		align={align}
	>
		{icon}
		{date.relative.text}
	</LabelWithPopover>
);

export {DateLabel};
