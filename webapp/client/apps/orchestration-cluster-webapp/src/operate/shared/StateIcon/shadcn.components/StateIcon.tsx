/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
	CircleAlert,
	CircleCheck,
	CircleDot,
	CircleHelp,
	CirclePause,
	CircleX,
	TriangleAlert,
} from '@camunda/design-system/icons';
import type {DecisionInstanceState, ProcessInstanceState} from '@camunda/camunda-api-zod-schemas/8.11';
import {cn} from '#/shared/cn';

type Props = {
	state: ProcessInstanceState | DecisionInstanceState | 'INCIDENT';
	size: React.ComponentProps<typeof TriangleAlert>['size'];
};

const stateIconsMap: Record<Props['state'], {Icon: typeof TriangleAlert; className?: string}> = {
	FAILED: {Icon: TriangleAlert, className: 'text-danger-foreground-subtle'},
	INCIDENT: {Icon: CircleAlert, className: 'text-danger-foreground-subtle'},
	ACTIVE: {Icon: CircleDot, className: 'text-success-foreground-subtle'},
	COMPLETED: {Icon: CircleCheck, className: 'text-neutral-foreground-strong'},
	EVALUATED: {Icon: CircleCheck, className: 'text-neutral-foreground-strong'},
	SUSPENDED: {Icon: CirclePause, className: 'text-neutral-foreground-subtle'},
	TERMINATED: {Icon: CircleX},
	UNSPECIFIED: {Icon: CircleHelp},
	UNKNOWN: {Icon: CircleHelp},
};

const StateIcon: React.FC<Props> = ({state, size}) => {
	const {Icon, className} = stateIconsMap[state];

	return (
		<Icon
			size={size}
			data-testid={`${state}-icon`}
			aria-hidden="true"
			focusable="false"
			className={cn('shrink-0', className)}
		/>
	);
};

export {StateIcon};
