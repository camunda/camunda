/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {ReactNode} from 'react';
import {Button, typographyVariants} from '@camunda/design-system';
import {cn} from '#/shared/cn';

type BulkAction = {
	key: string;
	label: string;
	icon?: ReactNode;
	disabled?: boolean;
	title?: string;
	onClick: () => void;
};

type Props = {
	selectedLabel: string;
	discardLabel: string;
	actions: BulkAction[];
	additionalActions?: ReactNode;
	onDiscard: () => void;
};

const BulkActionBar: React.FC<Props> = ({selectedLabel, discardLabel, actions, additionalActions, onDiscard}) => (
	<div className="flex items-center justify-between gap-4 border-b border-border bg-neutral-background-subtle px-4 py-2">
		<span role="status" className={cn('text-neutral-foreground', typographyVariants({variant: 'label-md'}))}>
			{selectedLabel}
		</span>
		<div className="flex items-center gap-2">
			{additionalActions}
			{actions.map(({key, label, icon, disabled, title, onClick}) => (
				<Button
					key={key}
					variant="ghost"
					size="sm"
					title={title}
					aria-disabled={disabled || undefined}
					className={cn(disabled && 'cursor-not-allowed opacity-50')}
					onClick={disabled ? undefined : onClick}
				>
					{icon}
					{label}
				</Button>
			))}
			<Button variant="secondary" size="sm" onClick={onDiscard}>
				{discardLabel}
			</Button>
		</div>
	</div>
);

export {BulkActionBar};
