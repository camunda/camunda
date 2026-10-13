/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Fragment, type ReactNode} from 'react';
import {Button, Tooltip, TooltipContent, TooltipTrigger, typographyVariants} from '@camunda/design-system';
import {cn} from '#/shared/cn';

type BulkAction = {
	key: string;
	label: string;
	icon?: ReactNode;
	disabled?: boolean;
	loading?: boolean;
	variant?: 'ghost' | 'destructive';
	disabledReason?: string;
	onClick: () => void;
};

type Props = {
	selectedLabel: string;
	discardLabel: string;
	actions: BulkAction[];
	additionalActions?: ReactNode;
	className?: string;
	onDiscard: () => void;
};

const BulkActionBar: React.FC<Props> = ({
	selectedLabel,
	discardLabel,
	actions,
	additionalActions,
	className,
	onDiscard,
}) => (
	<div
		data-slot="bulk-action-bar"
		className={cn(
			'flex items-center justify-between gap-4 border-b border-primary-border-subtle bg-primary-background-subtle px-4 py-2',
			className,
		)}
	>
		<span role="status" className={cn('text-primary-foreground-strong', typographyVariants({variant: 'label-md'}))}>
			{selectedLabel}
		</span>
		<div className="flex items-center gap-2">
			{additionalActions}
			{actions.map(({key, label, icon, disabled, loading, variant = 'ghost', disabledReason, onClick}) => {
				// aria-disabled keeps the button focusable so keyboard users can reach the reason
				const button = (
					<Button
						variant={variant}
						size="sm"
						loading={loading}
						aria-disabled={disabled || undefined}
						onClick={disabled ? undefined : onClick}
					>
						{icon}
						{label}
					</Button>
				);

				if (!disabled || disabledReason === undefined) {
					return <Fragment key={key}>{button}</Fragment>;
				}

				return (
					<Tooltip key={key}>
						<TooltipTrigger asChild>
							<span className="inline-flex">{button}</span>
						</TooltipTrigger>
						<TooltipContent>{disabledReason}</TooltipContent>
					</Tooltip>
				);
			})}
			<Button variant="ghost" size="sm" onClick={onDiscard}>
				{discardLabel}
			</Button>
		</div>
	</div>
);

export {BulkActionBar};
