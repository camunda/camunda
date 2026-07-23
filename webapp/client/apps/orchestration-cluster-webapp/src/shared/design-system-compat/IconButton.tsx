/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/**
 * Feature-flagged Carbon `IconButton` -> Camunda Design System composition.
 *
 * Unlike the plain ternary re-exports in `./index.ts`, `IconButton` has no
 * drop-in carbon-compat adapter — per `carbon-compat/MAPPING.md` ("IconButton"),
 * the DS target is `Button size="icon"` composed with `Tooltip`, not a 1:1
 * wrapper. That composition needs JSX, so it lives in its own `.tsx` file
 * here rather than inline in the `index.ts` barrel (which stays a flat,
 * JSX-free re-export list). `index.ts` re-exports `IconButton` from this file
 * so call sites keep importing everything from `#/shared/design-system-compat`.
 *
 * Prop transforms (see MAPPING.md "IconButton" row):
 * - `kind` -> `variant`: primary->default, secondary->secondary, tertiary->secondary, ghost->ghost
 * - `label` -> Tooltip content + `aria-label` fallback
 * - `align` -> `TooltipContent side`, prefix-mapped (`top-left` -> `top`, etc.)
 * - `enterDelayMs` -> `TooltipProvider delayDuration` (direct passthrough)
 * - `autoAlign` / `badgeCount` / `highContrast` -> dropped, no DS equivalent
 */

import type {ComponentProps, ReactNode} from 'react';
import {IconButton as CarbonIconButton} from '@carbon/react';
import {Button, Tooltip, TooltipContent, TooltipProvider, TooltipTrigger} from '@camunda/design-system';
import {featureFlags} from '#/shared/feature-flags';

type CarbonIconButtonProps = ComponentProps<typeof CarbonIconButton>;
type DsButtonVariant = ComponentProps<typeof Button>['variant'];
type DsButtonSize = ComponentProps<typeof Button>['size'];
type TooltipSide = ComponentProps<typeof TooltipContent>['side'];

type IconButtonKind = NonNullable<CarbonIconButtonProps['kind']>;
type IconButtonSize = NonNullable<CarbonIconButtonProps['size']>;

const KIND_TO_VARIANT: Record<IconButtonKind, DsButtonVariant> = {
	primary: 'default',
	secondary: 'secondary',
	tertiary: 'secondary',
	ghost: 'ghost',
};

const SIZE_TO_ICON_SIZE: Record<IconButtonSize, DsButtonSize> = {
	xs: 'icon-xs',
	sm: 'icon-sm',
	md: 'icon',
	lg: 'icon-lg',
};

// Carbon's `align` accepts a base direction with an optional corner suffix
// (e.g. `top-left`, `left-bottom`); `TooltipContent`'s `side` only takes the
// base direction, so take the segment before the first `-`.
function toTooltipSide(align: CarbonIconButtonProps['align']): TooltipSide {
	const base = (align ?? 'bottom').split('-')[0];
	return base as TooltipSide;
}

type IconButtonProps = Omit<ComponentProps<'button'>, 'size'> & {
	kind?: IconButtonKind;
	size?: IconButtonSize;
	align?: CarbonIconButtonProps['align'];
	label: ReactNode;
	enterDelayMs?: number;
};

/**
 * Each call renders its own `TooltipProvider`. The DS `Tooltip` primitive
 * intentionally ships without a built-in provider (see its source comment)
 * and expects "exactly one `TooltipProvider` at the app/page root" — this
 * repo does not have one yet, and adding it to `main.tsx` is out of scope
 * for a single-file migration. Sibling providers (as opposed to nested ones)
 * do not exhibit the open-state misfire the DS docs warn about, so this is
 * safe short-term; hoist to the app root once one exists.
 */
const DsIconButton = ({
	kind = 'primary',
	size = 'md',
	align,
	label,
	enterDelayMs,
	'aria-label': ariaLabelProp,
	children,
	...rest
}: IconButtonProps) => {
	const ariaLabel = ariaLabelProp ?? (typeof label === 'string' ? label : undefined);

	return (
		<TooltipProvider delayDuration={enterDelayMs}>
			<Tooltip>
				<TooltipTrigger asChild>
					<Button
						type="button"
						variant={KIND_TO_VARIANT[kind]}
						size={SIZE_TO_ICON_SIZE[size]}
						aria-label={ariaLabel}
						{...rest}
					>
						{children}
					</Button>
				</TooltipTrigger>
				<TooltipContent side={toTooltipSide(align)}>{label}</TooltipContent>
			</Tooltip>
		</TooltipProvider>
	);
};

export const IconButton = featureFlags.dsTasklistUI ? DsIconButton : CarbonIconButton;
