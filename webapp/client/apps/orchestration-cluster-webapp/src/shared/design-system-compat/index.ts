/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/**
 * Feature-flagged Carbon -> Camunda Design System swap point.
 *
 * Tasklist files migrating to the DS import from here instead of directly
 * from `@carbon/react` or `@camunda/design-system/carbon-compat`. When
 * `featureFlags.dsTasklistUI` is on, the DS-backed component is used;
 * otherwise the original Carbon component is used, unchanged.
 *
 * Add one named re-export per migrated symbol, following the pattern below.
 */

import {
	Button as CarbonButton,
	ComposedModal as CarbonComposedModal,
	InlineNotification as CarbonInlineNotification,
	Loading as CarbonLoading,
	Modal as CarbonModal,
	ModalBody as CarbonModalBody,
	ModalFooter as CarbonModalFooter,
	ModalHeader as CarbonModalHeader,
	OverflowMenu as CarbonOverflowMenu,
	OverflowMenuItem as CarbonOverflowMenuItem,
	Popover as CarbonPopover,
	PopoverContent as CarbonPopoverContent,
	Select as CarbonSelect,
	SelectItem as CarbonSelectItem,
	SkeletonText as CarbonSkeletonText,
	Stack as CarbonStack,
	Tag as CarbonTag,
	TableHeader as CarbonTableHeader,
	TextInput as CarbonTextInput,
	Toggle as CarbonToggle,
} from '@carbon/react';
import {
	Button as CompatButton,
	ComposedModal as CompatComposedModal,
	InlineNotification as CompatInlineNotification,
	Loading as CompatLoading,
	Modal as CompatModal,
	ModalBody as CompatModalBody,
	ModalFooter as CompatModalFooter,
	ModalHeader as CompatModalHeader,
	OverflowMenu as CompatOverflowMenu,
	OverflowMenuItem as CompatOverflowMenuItem,
	Popover as CompatPopover,
	PopoverContent as CompatPopoverContent,
	Select as CompatSelect,
	SelectItem as CompatSelectItem,
	SkeletonText as CompatSkeletonText,
	Stack as CompatStack,
	Tag as CompatTag,
	TableHeader as CompatTableHeader,
	TextInput as CompatTextInput,
	Toggle as CompatToggle,
} from '@camunda/design-system/carbon-compat';
import {
	CircleDash as CarbonCircleDash,
	Checkmark as CarbonCheckmark,
	Critical as CarbonCritical,
	Search as CarbonSearchIcon,
	SkillLevelAdvanced as CarbonSkillLevelAdvanced,
	SkillLevelBasic as CarbonSkillLevelBasic,
	SkillLevelIntermediate as CarbonSkillLevelIntermediate,
	SortAscending as CarbonSortAscending,
	UserAvatar as CarbonUserAvatar,
	UserAvatarFilled as CarbonUserAvatarFilled,
} from '@carbon/react/icons';
import {
	ArrowUpNarrowWide as LucideArrowUpNarrowWide,
	Check as LucideCheck,
	CircleDashed as LucideCircleDashed,
	CircleUser as LucideCircleUser,
	Search as LucideSearchIcon,
	SignalHigh as LucideSignalHigh,
	SignalLow as LucideSignalLow,
	SignalMedium as LucideSignalMedium,
	TriangleAlert as LucideTriangleAlert,
} from 'lucide-react';
import {featureFlags} from '#/shared/feature-flags';

// Re-exported for callers that need to type a local wrapper around `Tag`
// (e.g. `React.FC<TagProps<'div'> & ...>`). Carbon's own `Tag` component has
// a polymorphic call signature that also accepts `OperationalTagBaseProps` /
// `SelectableTagBaseProps` / `DismissibleTagBaseProps`; unioning that with
// the ternary above collapses `React.ComponentProps<typeof Tag>` into an
// unusable 5-way union. `TagProps<'div'>` (identical between Carbon and the
// compat adapter — the adapter's type is a re-export of Carbon's own type)
// is the correct, non-collapsing type for callers that only ever render the
// plain div-based Tag.
export type {TagProps} from '@camunda/design-system/carbon-compat';

// InlineNotification: carbon-compat ships an Alert-backed adapter with the
// same prop shape as Carbon's (kind -> variant, title/subtitle -> title/
// description, hideCloseButton -> dismissible inverted). Carbon-only props
// with no DS equivalent (statusIconDescription, iconDescription, actions,
// role, notificationType) are silently dropped by the adapter (dev-only
// console warning) rather than causing a type or runtime error.
export const InlineNotification = featureFlags.dsTasklistUI
	? CompatInlineNotification
	: CarbonInlineNotification;

export const Loading = featureFlags.dsTasklistUI ? CompatLoading : CarbonLoading;

// Modal: carbon-compat adapter is prop-compatible with Carbon's Modal API
// (danger, open, modalHeading, modalLabel, primaryButtonText,
// secondaryButtonText, onRequestClose, onRequestSubmit, children) — no JSX
// restructuring needed at the call site. `danger` switches the adapter's
// internal render from shadcn Dialog to AlertDialog. `size` and a few other
// Carbon-only props (`preventCloseOnClickOutside`, `loadingStatus`, etc.) are
// silently dropped by the adapter (dev-only console warning).
export const Modal = featureFlags.dsTasklistUI ? CompatModal : CarbonModal;

// ComposedModal / ModalHeader / ModalBody / ModalFooter: carbon-compat ships a
// prop-compatible compound-children adapter (types are Carbon's own
// ComposedModalProps/ModalHeaderProps/ModalFooterProps re-exported), backed by
// shadcn Dialog under the hood — no JSX restructuring needed at the call
// site. ModalHeader's `buttonOnClick` is silently dropped (dev-only console
// warning): DialogContent already renders its own close (X) button wired to
// the same onClose/onOpenChange bridge, so the close behavior is preserved
// even though the prop itself is unused. `preventCloseOnClickOutside` and
// `size` on ComposedModal are dropped the same way (documented in
// carbon-compat/MAPPING.md).
export const ComposedModal = featureFlags.dsTasklistUI ? CompatComposedModal : CarbonComposedModal;
export const ModalHeader = featureFlags.dsTasklistUI ? CompatModalHeader : CarbonModalHeader;
export const ModalBody = featureFlags.dsTasklistUI ? CompatModalBody : CarbonModalBody;
export const ModalFooter = featureFlags.dsTasklistUI ? CompatModalFooter : CarbonModalFooter;

export const SkeletonText = featureFlags.dsTasklistUI ? CompatSkeletonText : CarbonSkeletonText;
export const Stack = featureFlags.dsTasklistUI ? CompatStack : CarbonStack;

// Tag: carbon-compat adapter is backed by DS Badge under the hood and keeps
// the same call-site shape (children, className, ...rest passed through).
// `size`, `title`, `disabled`, `filter`, `onClose`, `decorator`, and `slug`
// have no Badge equivalent and are silently dropped (dev-only console
// warning) rather than causing a type or runtime error. Callers relying on
// `title` for a tooltip/aria text or on `size` for Carbon's own sm/md
// dimensions should compensate via `className`/CSS — `size` is still
// accepted as a prop (no type error) but has no visual effect once the flag
// is on.
export const Tag = featureFlags.dsTasklistUI ? CompatTag : CarbonTag;
export const SearchIcon = featureFlags.dsTasklistUI ? LucideSearchIcon : CarbonSearchIcon;
export const Popover = featureFlags.dsTasklistUI ? CompatPopover : CarbonPopover;
export const PopoverContent = featureFlags.dsTasklistUI ? CompatPopoverContent : CarbonPopoverContent;
export const Select = featureFlags.dsTasklistUI ? CompatSelect : CarbonSelect;
export const SelectItem = featureFlags.dsTasklistUI ? CompatSelectItem : CarbonSelectItem;
export const Toggle = featureFlags.dsTasklistUI ? CompatToggle : CarbonToggle;
export const Button = featureFlags.dsTasklistUI ? CompatButton : CarbonButton;
export const TextInput = featureFlags.dsTasklistUI ? CompatTextInput : CarbonTextInput;

// carbon-compat ships an OverflowMenu/OverflowMenuItem adapter with the same
// prop shape as Carbon's (backed by DS DropdownMenu under the hood), so no
// local wrapper is needed — the Carbon and compat versions are interchangeable
// at the call site. Positioning/sizing props with no DS equivalent
// (`menuOptionsClass`, `size`, `align`, etc.) are silently dropped by the
// adapter (dev-only console warning) rather than causing a type or runtime
// error.
export const OverflowMenu = featureFlags.dsTasklistUI ? CompatOverflowMenu : CarbonOverflowMenu;
export const OverflowMenuItem = featureFlags.dsTasklistUI ? CompatOverflowMenuItem : CarbonOverflowMenuItem;

// TableHeader: carbon-compat adapter silently drops the Carbon-specific sorting
// props (`isSortHeader`, `isSortable`, `sortDirection`) since the DS table
// component does not support them. Callers implementing sorting via external
// handlers (e.g., URL navigation) are unaffected; the visual/semantic props
// are dropped but the onClick handler and accessibility attributes are preserved.
export const TableHeader = featureFlags.dsTasklistUI ? CompatTableHeader : CarbonTableHeader;

// Checkmark: generic UI icon (active-sort-option indicator), not a domain
// entity — no registry match. Lucide's `Check` is the direct equivalent
// (see docs/kb/carbon-icons-to-lucide.md "Common remaps").
export const CheckmarkIcon = featureFlags.dsTasklistUI ? LucideCheck : CarbonCheckmark;

// SortAscending: generic UI icon (sort-trigger icon), not a domain entity.
// Not listed in carbon-icons-to-lucide.md's mapping table, but Lucide's
// `ArrowUpNarrowWide` is the library's standard "sort ascending" glyph
// (arrow-up + narrow-to-wide bars) and is the closest visual/semantic match.
export const SortAscendingIcon = featureFlags.dsTasklistUI ? LucideArrowUpNarrowWide : CarbonSortAscending;

// Carbon's SkillLevel* icons are used here as priority-level indicators
// (low/medium/high signal bars), not a Camunda domain entity — no registry
// match. Lucide's Signal* icons are the closest visual equivalent (bar-style
// severity indicators). `Critical` maps to `TriangleAlert`, consistent with
// the existing Carbon `WarningFilled`/`Caution` -> `TriangleAlert` precedent.
export const SkillLevelBasicIcon = featureFlags.dsTasklistUI ? LucideSignalLow : CarbonSkillLevelBasic;
export const SkillLevelIntermediateIcon = featureFlags.dsTasklistUI
	? LucideSignalMedium
	: CarbonSkillLevelIntermediate;
export const SkillLevelAdvancedIcon = featureFlags.dsTasklistUI ? LucideSignalHigh : CarbonSkillLevelAdvanced;
export const CriticalIcon = featureFlags.dsTasklistUI ? LucideTriangleAlert : CarbonCritical;

// CircleDash: used here as a generic "unassigned" indicator, not a Camunda
// domain entity — no registry match. Lucide's `CircleDashed` is the direct
// visual and semantic equivalent (dashed-outline circle).
export const CircleDashIcon = featureFlags.dsTasklistUI ? LucideCircleDashed : CarbonCircleDash;

// UserAvatar / UserAvatarFilled: generic person-avatar glyphs used as an
// assignee indicator, not a bound Camunda "user" domain entity (the registry's
// `user` key -> UserIcon covers Carbon's plain `User` glyph, not the
// avatar-in-circle variants used here) — no registry match. Lucide's
// `CircleUser` is the closest visual equivalent (person inside a circle).
// Lucide ships stroke-only icons with no filled/outline pairing the way
// Carbon does, so both the outlined (`UserAvatar`, assigned-to-someone-else)
// and filled (`UserAvatarFilled`, assigned-to-me) Carbon glyphs map to the
// same `CircleUser` icon once the flag is on. The assigned-to-me vs.
// assigned-to-other distinction is preserved by the Tag's own
// `$isHighlighted`/`highlighted` background styling (see AssigneeTag.tsx),
// not by icon shape.
export const UserAvatarIcon = featureFlags.dsTasklistUI ? LucideCircleUser : CarbonUserAvatar;
export const UserAvatarFilledIcon = featureFlags.dsTasklistUI ? LucideCircleUser : CarbonUserAvatarFilled;
