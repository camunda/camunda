/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {IconButton} from '@camunda/design-system';
import {Ban, Pause, Play, RotateCcw, Wrench} from '@camunda/design-system/icons';

type ButtonSize = 'xs' | 'sm' | 'md' | 'lg' | 'xl' | '2xl';

type ItemProps = {
	type:
		| 'RESOLVE_INCIDENT'
		| 'CANCEL_PROCESS_INSTANCE'
		| 'SUSPEND_PROCESS_INSTANCE'
		| 'RESUME_PROCESS_INSTANCE'
		| 'ENTER_MODIFICATION_MODE';
	onClick: React.ComponentProps<'button'>['onClick'];
	title: string;
	disabled?: boolean;
	size?: ButtonSize;
};

const TYPE_DETAILS: Readonly<
	Record<ItemProps['type'], {icon: React.ComponentProps<typeof IconButton>['icon']; testId: string}>
> = {
	RESOLVE_INCIDENT: {icon: RotateCcw, testId: 'retry-operation'},
	CANCEL_PROCESS_INSTANCE: {icon: Ban, testId: 'cancel-operation'},
	SUSPEND_PROCESS_INSTANCE: {icon: Pause, testId: 'suspend-operation'},
	RESUME_PROCESS_INSTANCE: {icon: Play, testId: 'resume-operation'},
	ENTER_MODIFICATION_MODE: {icon: Wrench, testId: 'enter-modification-mode'},
};

const SIZE_MAP: Record<ButtonSize, React.ComponentProps<typeof IconButton>['size']> = {
	xs: 'xs',
	sm: 'sm',
	md: 'default',
	lg: 'lg',
	xl: 'lg',
	'2xl': 'lg',
};

const OperationItem: React.FC<ItemProps> = ({title, onClick, type, disabled, size}) => {
	const {icon, testId} = TYPE_DETAILS[type];

	return (
		<li>
			<IconButton
				icon={icon}
				label={title}
				variant="ghost"
				size={size === undefined ? 'default' : SIZE_MAP[size]}
				tooltipSide="left"
				onClick={onClick}
				disabled={disabled}
				data-testid={testId}
				title={title}
			/>
		</li>
	);
};

export {OperationItem};
