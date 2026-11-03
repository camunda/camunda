/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {CircleCheck, CircleX} from '@camunda/design-system/icons';
import type {AuditLogResult} from '@camunda/camunda-api-zod-schemas/8.11/audit-log';
import {cn} from '#/shared/cn';

const resultIconsMap = {
	FAIL: CircleX,
	SUCCESS: CircleCheck,
} as const;

type Props = {
	state: AuditLogResult;
};

const OperationsLogResultIcon: React.FC<Props> = ({state}) => {
	const TargetComponent = resultIconsMap[state];

	return (
		<TargetComponent
			size={20}
			aria-hidden="true"
			focusable="false"
			className={cn('shrink-0', {
				'text-danger-foreground-subtle': state === 'FAIL',
				'text-success-foreground-subtle': state === 'SUCCESS',
			})}
		/>
	);
};

export {OperationsLogResultIcon};
