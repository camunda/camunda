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
	Column as CarbonColumn,
	ComposedModal as CarbonComposedModal,
	ContainedList as CarbonContainedList,
	ContainedListItem as CarbonContainedListItem,
	DatePicker as CarbonDatePicker,
	DatePickerInput as CarbonDatePickerInput,
	Dropdown as CarbonDropdown,
	Search as CarbonSearch,
	FormGroup as CarbonFormGroup,
	Grid as CarbonGrid,
	InlineLoading as CarbonInlineLoading,
	InlineNotification as CarbonInlineNotification,
	Layer as CarbonLayer,
	Link as CarbonLink,
	Loading as CarbonLoading,
	Modal as CarbonModal,
	ModalBody as CarbonModalBody,
	ModalFooter as CarbonModalFooter,
	ModalHeader as CarbonModalHeader,
	OverflowMenu as CarbonOverflowMenu,
	OverflowMenuItem as CarbonOverflowMenuItem,
	Popover as CarbonPopover,
	PopoverContent as CarbonPopoverContent,
	RadioButton as CarbonRadioButton,
	RadioButtonGroup as CarbonRadioButtonGroup,
	Section as CarbonSection,
	Select as CarbonSelect,
	SelectItem as CarbonSelectItem,
	SelectSkeleton as CarbonSelectSkeleton,
	SkeletonText as CarbonSkeletonText,
	Stack as CarbonStack,
	Heading as CarbonHeading,
	StructuredListBody as CarbonStructuredListBody,
	StructuredListCell as CarbonStructuredListCell,
	StructuredListHead as CarbonStructuredListHead,
	StructuredListRow as CarbonStructuredListRow,
	StructuredListWrapper as CarbonStructuredListWrapper,
	Tag as CarbonTag,
	TableHeader as CarbonTableHeader,
	Tab as CarbonTab,
	TabList as CarbonTabList,
	Tabs as CarbonTabs,
	TabsSkeleton as CarbonTabsSkeleton,
	TextInput as CarbonTextInput,
	Toggle as CarbonToggle,
} from '@carbon/react';
import {
	Button as CompatButton,
	Column as CompatColumn,
	ComposedModal as CompatComposedModal,
	ContainedList as CompatContainedList,
	ContainedListItem as CompatContainedListItem,
	DatePicker as CompatDatePicker,
	DatePickerInput as CompatDatePickerInput,
	Dropdown as CompatDropdown,
	Search as CompatSearch,
	FormGroup as CompatFormGroup,
	Grid as CompatGrid,
	InlineLoading as CompatInlineLoading,
	InlineNotification as CompatInlineNotification,
	Layer as CompatLayer,
	Link as CompatLink,
	Loading as CompatLoading,
	Modal as CompatModal,
	ModalBody as CompatModalBody,
	ModalFooter as CompatModalFooter,
	ModalHeader as CompatModalHeader,
	OverflowMenu as CompatOverflowMenu,
	OverflowMenuItem as CompatOverflowMenuItem,
	Popover as CompatPopover,
	PopoverContent as CompatPopoverContent,
	RadioButton as CompatRadioButton,
	RadioButtonGroup as CompatRadioButtonGroup,
	Section as CompatSection,
	Select as CompatSelect,
	SelectItem as CompatSelectItem,
	SelectSkeleton as CompatSelectSkeleton,
	SkeletonText as CompatSkeletonText,
	Stack as CompatStack,
	Heading as CompatHeading,
	StructuredListBody as CompatStructuredListBody,
	StructuredListCell as CompatStructuredListCell,
	StructuredListHead as CompatStructuredListHead,
	StructuredListRow as CompatStructuredListRow,
	StructuredListWrapper as CompatStructuredListWrapper,
	Tag as CompatTag,
	TableHeader as CompatTableHeader,
	Tab as CompatTab,
	TabList as CompatTabList,
	Tabs as CompatTabs,
	TabsSkeleton as CompatTabsSkeleton,
	TextInput as CompatTextInput,
	Toggle as CompatToggle,
} from '@camunda/design-system/carbon-compat';
import {
	Add as CarbonAdd,
	Calendar as CarbonCalendar,
	CenterCircle as CarbonCenterCircle,
	CheckmarkFilled as CarbonCheckmarkFilled,
	CircleDash as CarbonCircleDash,
	Checkmark as CarbonCheckmark,
	Close as CarbonClose,
	Critical as CarbonCritical,
	EventSchedule as CarbonEventSchedule,
	Filter as CarbonFilter,
	Information as CarbonInformation,
	ArrowRight as CarbonArrowRight,
	List as CarbonList,
	Maximize as CarbonMaximize,
	Launch as CarbonLaunch,
	Notification as CarbonNotification,
	Search as CarbonSearchIcon,
	Share as CarbonShare,
	SidePanelClose as CarbonSidePanelClose,
	SidePanelOpen as CarbonSidePanelOpen,
	SkillLevelAdvanced as CarbonSkillLevelAdvanced,
	SkillLevelBasic as CarbonSkillLevelBasic,
	SkillLevelIntermediate as CarbonSkillLevelIntermediate,
	SortAscending as CarbonSortAscending,
	Subtract as CarbonSubtract,
	UserAvatar as CarbonUserAvatar,
	UserAvatarFilled as CarbonUserAvatarFilled,
	Warning as CarbonWarning,
} from '@carbon/react/icons';
import {
	ArrowUpNarrowWide as LucideArrowUpNarrowWide,
	Bell as LucideBell,
	Calendar as LucideCalendar,
	CalendarClock as LucideCalendarClock,
	Check as LucideCheck,
	ChevronsDown as LucideChevronsDown,
	ChevronsUp as LucideChevronsUp,
	CircleCheck as LucideCircleCheck,
	CircleDashed as LucideCircleDashed,
	CircleUser as LucideCircleUser,
	Crosshair as LucideCrosshair,
	Equal as LucideEqual,
	ExternalLink as LucideExternalLink,
	Filter as LucideFilter,
	Info as LucideInfo,
	ArrowRight as LucideArrowRight,
	List as LucideList,
	Maximize2 as LucideMaximize2,
	PanelLeftClose as LucidePanelLeftClose,
	PanelLeftOpen as LucidePanelLeftOpen,
	Plus as LucidePlus,
	Search as LucideSearchIcon,
	Share2 as LucideShare2,
	TriangleAlert as LucideTriangleAlert,
	X as LucideX,
	ZoomIn as LucideZoomIn,
	ZoomOut as LucideZoomOut,
} from 'lucide-react';
import {featureFlags} from '#/shared/feature-flags';
import {IconButton} from './IconButton';

export {IconButton};

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

// InlineLoadingProps: carbon-compat re-exports Carbon's own InlineLoading type
// unchanged (see carbon-compat/inline-loading.d.ts), so this is a type-only
// re-export with no behavioral or shape difference from importing directly
// from `@carbon/react`.
export type {InlineLoadingProps} from '@camunda/design-system/carbon-compat';

// ButtonProps: same collapsing-union problem as `Tag` above. Carbon's own
// `Button` is polymorphic (`ButtonProps<T extends React.ElementType>`, no
// default), while the compat adapter's exported function signature is fixed
// to `ButtonProps<"button">`. Typing a callsite prop as
// `React.ComponentProps<typeof Button>` unions the two and widens `as` to
// `ElementType<any, keyof IntrinsicElements> | undefined`, which then fails
// to satisfy either component's narrower `as` requirement. `ButtonProps`
// (identical between Carbon and the compat adapter — the adapter's type is a
// re-export of Carbon's own generic type, defaulted to `"button"`) is the
// correct, non-collapsing type for callers that only ever render the plain
// `<button>`-based Button.
export type {ButtonProps} from '@camunda/design-system/carbon-compat';

// InlineLoading: SWAP tier (docs/kb/carbon-migration-tiers.md) — carbon-compat
// re-exports the component unchanged (paper-move, no shadcn equivalent yet).
// Zero prop/behavior difference from importing directly from `@carbon/react`.
export const InlineLoading = featureFlags.dsTasklistUI ? CompatInlineLoading : CarbonInlineLoading;

// InlineNotification: carbon-compat ships an Alert-backed adapter with the
// same prop shape as Carbon's (kind -> variant, title/subtitle -> title/
// description, hideCloseButton -> dismissible inverted). Carbon-only props
// with no DS equivalent (statusIconDescription, iconDescription, actions,
// role, notificationType) are silently dropped by the adapter (dev-only
// console warning) rather than causing a type or runtime error.
export const InlineNotification = featureFlags.dsTasklistUI ? CompatInlineNotification : CarbonInlineNotification;

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

// Layer: carbon-compat currently re-exports this unchanged from `@carbon/react`
// (Carbon's z-layer theming system has no shadcn equivalent — see
// carbon-compat/layer.tsx and MAPPING.md's SHIM table). Importing through this
// feature-flagged swap point today is a no-op visually/behaviorally, but
// establishes the swap point so call sites pick up a real DS-backed
// implementation automatically if one ships, with no further call-site changes.
export const Layer = featureFlags.dsTasklistUI ? CompatLayer : CarbonLayer;

// Link: carbon-compat adapter renders a DS-token-styled `<a>` with the same
// prop shape as Carbon's `LinkProps<'a'>` (size, disabled, renderIcon,
// className, children, ...rest) — no JSX restructuring needed at the call
// site. `inline` has no separate spacing treatment in the adapter and is
// silently dropped (dev-only console warning); callers relying on Carbon's
// inline variant get the standard link look once the flag is on.
export const Link = featureFlags.dsTasklistUI ? CompatLink : CarbonLink;

// Grid / Column: SHIM tier (carbon-compat/MAPPING.md "SHIM components" table) —
// carbon-compat re-exports these unchanged from `@carbon/react` (layout-only
// primitives; the DS equivalent is Tailwind grid utilities, so no adapter
// ships yet — see carbon-compat/grid.tsx / column.tsx). Importing through this
// feature-flagged swap point today is a no-op visually/behaviorally (the
// responsive `sm`/`md`/`lg`/`xlg` span+offset props pass straight through), but
// establishes the swap point so call sites pick up a real DS-backed
// implementation automatically if one ships, with no further call-site changes.
export const Grid = featureFlags.dsTasklistUI ? CompatGrid : CarbonGrid;
export const Column = featureFlags.dsTasklistUI ? CompatColumn : CarbonColumn;

// StructuredList family: carbon-compat currently re-exports these unchanged
// from `@carbon/react` (no shadcn equivalent yet — see
// carbon-compat/structured-list.tsx and MAPPING.md's SHIM table). Importing
// through this feature-flagged swap point today is a no-op visually/
// behaviorally, but establishes the swap point so the call site picks up a
// real DS-backed implementation automatically once one ships, with no
// further call-site changes required.
export const StructuredListWrapper = featureFlags.dsTasklistUI
	? CompatStructuredListWrapper
	: CarbonStructuredListWrapper;
export const StructuredListBody = featureFlags.dsTasklistUI ? CompatStructuredListBody : CarbonStructuredListBody;
export const StructuredListHead = featureFlags.dsTasklistUI ? CompatStructuredListHead : CarbonStructuredListHead;
export const StructuredListRow = featureFlags.dsTasklistUI ? CompatStructuredListRow : CarbonStructuredListRow;
export const StructuredListCell = featureFlags.dsTasklistUI ? CompatStructuredListCell : CarbonStructuredListCell;

// Heading: carbon-compat re-exports Carbon's semantic heading-level component
// unchanged (paper-move SHIM, same family as Section above). The `level`
// context Carbon derives from an enclosing Section is preserved either way.
export const Heading = featureFlags.dsTasklistUI ? CompatHeading : CarbonHeading;

// Section: carbon-compat currently re-exports this unchanged from `@carbon/react`
// (semantic heading-level wrapper — paper-move SHIM, no shadcn equivalent yet;
// see carbon-compat/section.d.ts). The `level` prop (used at the call site) is
// Carbon's own and is fully preserved. Importing through this feature-flagged
// swap point today is a no-op visually/behaviorally, but establishes the swap
// point so the call site picks up a real DS-backed implementation automatically
// once one ships, with no further call-site changes required.
export const Section = featureFlags.dsTasklistUI ? CompatSection : CarbonSection;

// ContainedList / ContainedListItem: SHIM tier (docs/kb/carbon-migration-tiers.md).
// carbon-compat currently re-exports both unchanged from `@carbon/react` (no
// shadcn equivalent yet — see carbon-compat/contained-list.d.ts). Importing
// through this feature-flagged swap point today is a no-op visually/
// behaviorally, but establishes the swap point so the call site picks up a
// real DS-backed implementation automatically once one ships, with no further
// call-site changes required. The `label` and `kind` props (used at the call
// site) are Carbon's own and are fully preserved.
export const ContainedList = featureFlags.dsTasklistUI ? CompatContainedList : CarbonContainedList;
export const ContainedListItem = featureFlags.dsTasklistUI ? CompatContainedListItem : CarbonContainedListItem;

// Tabs/TabList/Tab: SWAP tier. carbon-compat's adapter re-implements Carbon's
// index-based Tabs/TabList/Tab API (selectedIndex/onChange) on top of the DS's
// Radix-backed Tabs, mapping index <-> a synthetic `tab-<n>` value internally.
// No JSX restructuring needed at call sites using this same index-based API.
export const Tabs = featureFlags.dsTasklistUI ? CompatTabs : CarbonTabs;
export const TabList = featureFlags.dsTasklistUI ? CompatTabList : CarbonTabList;
export const Tab = featureFlags.dsTasklistUI ? CompatTab : CarbonTab;

// TabsSkeleton: SWAP tier (docs/kb/carbon-migration-tiers.md). carbon-compat
// ships a Tailwind `animate-pulse` adapter that renders a tab-bar-shaped
// placeholder matching the dimensions Carbon's static skeleton occupies, so the
// subsequent real <Tabs> swap-in doesn't reflow. Carbon's `type`
// ('default' | 'container') is accepted but unused at this fidelity (skeleton
// renders identically either way); `className` is applied to the wrapper.
export const TabsSkeleton = featureFlags.dsTasklistUI ? CompatTabsSkeleton : CarbonTabsSkeleton;

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

// SelectSkeleton: SWAP tier (docs/kb/carbon-migration-tiers.md). carbon-compat
// ships a Tailwind `animate-pulse` adapter that renders a Select-shaped
// placeholder matching the dimensions Carbon's static skeleton occupies, so the
// subsequent real <Select> swap-in doesn't reflow. `hideLabel` and `className`
// (Carbon's own props, type re-exported) are preserved; no other props exist.
export const SelectSkeleton = featureFlags.dsTasklistUI ? CompatSelectSkeleton : CarbonSelectSkeleton;

// FormGroup: carbon-compat adapter renders a native `<fieldset>` + `<legend>`
// with the same prop shape as Carbon's (type is Carbon's own FormGroupProps
// re-exported) — `legendText`, `legendId`, `disabled`, `invalid`, `className`
// are preserved, so no JSX restructuring is needed at the call site. Only
// `message`/`messageText` (Carbon's inline error-helper variant) are silently
// dropped by the adapter (dev-only console warning); callers not using them are
// unaffected.
export const FormGroup = featureFlags.dsTasklistUI ? CompatFormGroup : CarbonFormGroup;

// RadioButton / RadioButtonGroup: carbon-compat ships a prop-compatible adapter
// (types are Carbon's own RadioButtonProps/RadioButtonGroupProps re-exported),
// backed by Radix RadioGroup under the hood — no JSX restructuring needed at the
// call site. The group bridges Carbon's `valueSelected`/`defaultSelected`/
// `onChange` to Radix's `value`/`defaultValue`/`onValueChange`; `legendText`,
// `orientation`, `name`, and `className` are preserved. Note: the adapter only
// bridges `onChange` at the group level — an `onChange` placed on an individual
// `RadioButton` falls into `...rest` and never fires (this file already wires
// `onChange` on the group, so no change is needed here).
export const RadioButton = featureFlags.dsTasklistUI ? CompatRadioButton : CarbonRadioButton;
export const RadioButtonGroup = featureFlags.dsTasklistUI ? CompatRadioButtonGroup : CarbonRadioButtonGroup;

// DatePicker / DatePickerInput: SHIM tier (docs/kb/carbon-migration-tiers.md) —
// carbon-compat ships a compound adapter that keeps Carbon's parent/child call
// shape (`<DatePicker datePickerType dateFormat onChange locale><DatePickerInput
// id labelText placeholder size/></DatePicker>`): the parent reads the child's
// input props via `findInputProps`, and `DatePickerInput` renders nothing on its
// own. `datePickerType`, `dateFormat`, `onChange` (Date[] signature),
// `className`, `id`, `labelText`, `placeholder`, and `size` are all preserved,
// so no JSX restructuring is needed at the call site.
export const DatePicker = featureFlags.dsTasklistUI ? CompatDatePicker : CarbonDatePicker;
export const DatePickerInput = featureFlags.dsTasklistUI ? CompatDatePickerInput : CarbonDatePickerInput;

// Dropdown: carbon-compat adapter's exported type is Carbon's own DropdownProps
// re-exported, so no JSX restructuring is needed at the call site. `size` and
// `direction` have no Radix-backed equivalent and are silently dropped (dev-only
// console warning) rather than causing a type or runtime error — callers passing
// `direction="top"` should expect the menu to open downward once the flag is on
// and verify visually.
export const Dropdown = featureFlags.dsTasklistUI ? CompatDropdown : CarbonDropdown;

// Search: carbon-compat ships a real adapter (DS Input plus a leading search
// icon) rather than a passthrough, and it keeps Carbon's prop shape, so call
// sites need no JSX changes. Note the name clash with `SearchIcon` above —
// that one is the Carbon *icon*, this is the input component.
export const Search = featureFlags.dsTasklistUI ? CompatSearch : CarbonSearch;

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

// CheckmarkFilled: generic "task completed" status icon, not a domain
// entity — no registry match. Lucide's `CircleCheck` is the documented direct
// equivalent (see docs/kb/carbon-icons-to-lucide.md's mapping table, which
// notes Carbon's filled variant needs `fill-current` to approximate the solid
// look — callers should add that class where the filled style matters).
export const CheckmarkFilledIcon = featureFlags.dsTasklistUI ? LucideCircleCheck : CarbonCheckmarkFilled;

// Calendar: generic date/schedule glyph (task creation-date row), not a
// domain entity — no registry match. Lucide ships an identically-named
// `Calendar` icon — direct equivalent, no visual approximation needed.
export const CalendarIcon = featureFlags.dsTasklistUI ? LucideCalendar : CarbonCalendar;

// Warning: generic "overdue" status icon, not a domain entity — no registry
// match. Not listed verbatim in docs/kb/carbon-icons-to-lucide.md's mapping
// table (that table only has `WarningAlt`/`WarningAltFilled`/`WarningFilled`/
// `Caution`), but Lucide's `TriangleAlert` is the same glyph family and is
// already the established mapping for Carbon's `Critical` (see `CriticalIcon`
// below) — reused here for consistency. Callers should add `fill-current`
// where the filled-triangle look matters, same as the `CheckmarkFilled` note
// above.
export const WarningIcon = featureFlags.dsTasklistUI ? LucideTriangleAlert : CarbonWarning;

// Notification: generic "follow-up date" status icon, not a domain entity —
// no registry match, and not listed in docs/kb/carbon-icons-to-lucide.md at
// all (genuine gap). Lucide's `Bell` is the closest visual/semantic
// equivalent (notification/reminder glyph) — fallback tier per the KB's
// "no reasonable equivalent" guidance. Logged under "Icon gaps (Carbon ->
// Lucide)" in docs/migration/human-follow-up.md.
export const NotificationIcon = featureFlags.dsTasklistUI ? LucideBell : CarbonNotification;

// SortAscending: generic UI icon (sort-trigger icon), not a domain entity.
// Not listed in carbon-icons-to-lucide.md's mapping table, but Lucide's
// `ArrowUpNarrowWide` is the library's standard "sort ascending" glyph
// (arrow-up + narrow-to-wide bars) and is the closest visual/semantic match.
export const SortAscendingIcon = featureFlags.dsTasklistUI ? LucideArrowUpNarrowWide : CarbonSortAscending;

// Carbon's SkillLevel* icons are used here as priority-level indicators
// (low/medium/high), not a Camunda domain entity — no registry match.
// Explicit choice (not the KB's default Signal* mapping): `ChevronsDown`/
// `ChevronsUp` for low/high (directional magnitude reads clearer than bar
// height at this icon size), `Equal` for medium (neither up nor down).
// `Critical` maps to `TriangleAlert`, consistent with the existing Carbon
// `WarningFilled`/`Caution` -> `TriangleAlert` precedent.
export const SkillLevelBasicIcon = featureFlags.dsTasklistUI ? LucideChevronsDown : CarbonSkillLevelBasic;
export const SkillLevelIntermediateIcon = featureFlags.dsTasklistUI ? LucideEqual : CarbonSkillLevelIntermediate;
export const SkillLevelAdvancedIcon = featureFlags.dsTasklistUI ? LucideChevronsUp : CarbonSkillLevelAdvanced;
export const CriticalIcon = featureFlags.dsTasklistUI ? LucideTriangleAlert : CarbonCritical;

// EventSchedule: used here as a generic calendar/time-of-event glyph (audit
// log timestamp row), not a "backup" domain entity — no registry match under
// the context rule in docs/kb/carbon-icons-to-lucide.md ("EventSchedule
// (backup context)" only REMAPs to the registry's BackupIcon when the
// surrounding code refers to a backup). Lucide's `CalendarClock` is the
// documented generic-calendar equivalent (see the KB's "Mapping table",
// `EventSchedule (calendar)` row).
export const EventScheduleIcon = featureFlags.dsTasklistUI ? LucideCalendarClock : CarbonEventSchedule;

// Launch: generic "open in new tab" affordance (external docs link), not a
// domain entity. Lucide's `ExternalLink` is the documented direct equivalent
// (see docs/kb/carbon-icons-to-lucide.md's mapping table).
export const LaunchIcon = featureFlags.dsTasklistUI ? LucideExternalLink : CarbonLaunch;

// Information: generic informational "i-in-circle" affordance (the history
// row "view details" link glyph), not a Camunda domain entity — no registry
// match. Lucide's `Info` is the direct visual/semantic equivalent (a circle
// enclosing a lowercase "i") — same-glyph mapping, no approximation needed.
export const InformationIcon = featureFlags.dsTasklistUI ? LucideInfo : CarbonInformation;

// Maximize: genuine icon gap — Carbon's `Maximize` has no row in
// docs/kb/carbon-icons-to-lucide.md at all. Lucide's `Maximize2` (opposing
// diagonal arrows) is the same glyph Carbon renders here, and reads as
// "expand this value into the editor" at the variables-table call site.
// Lucide also ships `Maximize` (four corner brackets), which is a different
// glyph — not the match. Logged as an icon gap in human-follow-up.md.
export const MaximizeIcon = featureFlags.dsTasklistUI ? LucideMaximize2 : CarbonMaximize;

// ArrowRight: documented as an identical-name remap in carbon-icons-to-lucide.md.
export const ArrowRightIcon = featureFlags.dsTasklistUI ? LucideArrowRight : CarbonArrowRight;

// List: not in carbon-icons-to-lucide.md. Used on the process tile's "requires
// form input" tag, where it reads as "this has fields to fill in". Lucide's
// `List` is the same bulleted-list glyph — direct match, not an approximation.
// Logged as an icon gap in human-follow-up.md.
export const ListIcon = featureFlags.dsTasklistUI ? LucideList : CarbonList;

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

// Add / Subtract / CenterCircle: diagram zoom-control glyphs (process
// diagram zoom in/out/reset), not domain entities — no registry match. Not
// listed verbatim in docs/kb/carbon-icons-to-lucide.md (that KB's "Common
// remaps" maps generic `Add` -> `Plus`), but in this zoom-control context
// Lucide's dedicated `ZoomIn`/`ZoomOut`/`Crosshair` glyphs are the closer
// semantic match than the generic add/subtract/target icons — same choice
// used consistently anywhere this diagram zoom control appears.
export const AddIcon = featureFlags.dsTasklistUI ? LucideZoomIn : CarbonAdd;
export const SubtractIcon = featureFlags.dsTasklistUI ? LucideZoomOut : CarbonSubtract;
export const CenterCircleIcon = featureFlags.dsTasklistUI ? LucideCrosshair : CarbonCenterCircle;

// Close: generic UI icon (X / close affordance on the remove-variable button),
// not a Camunda domain entity — no registry match. Lucide's `X` is the
// documented direct equivalent (see docs/kb/carbon-icons-to-lucide.md's
// "Common remaps": `Close` -> `X`).
export const CloseIcon = featureFlags.dsTasklistUI ? LucideX : CarbonClose;

// Plus: generic "add" affordance (the add-variable-row button's leading icon),
// not a domain entity — no registry match. Carbon's `Add` glyph in this generic
// add context maps to Lucide's `Plus` per docs/kb/carbon-icons-to-lucide.md's
// "Common remaps" (generic `Add` -> `Plus`). NOTE: the `AddIcon` export above
// deliberately maps Carbon `Add` -> Lucide `ZoomIn` for the process-diagram
// zoom-control context *only*; this generic add/plus usage gets its own export
// so it doesn't inherit the zoom glyph. Both share the same Carbon fallback
// (`CarbonAdd`) since both were Carbon's `Add` pre-migration.
export const PlusIcon = featureFlags.dsTasklistUI ? LucidePlus : CarbonAdd;

// Filter: generic "filter tasks" affordance on the collapsed task-nav panel,
// not a Camunda domain entity — no registry match. Lucide ships an
// identically-named `Filter` (funnel) glyph — direct visual/semantic
// equivalent, no approximation needed.
export const FilterIcon = featureFlags.dsTasklistUI ? LucideFilter : CarbonFilter;

// SidePanelOpen / SidePanelClose: generic panel-toggle glyphs for the
// left-docked task filter navigation panel (`id="task-nav-bar"`), not domain
// entities — no registry match. The panel is left-docked, so Lucide's
// `PanelLeft*` family is the direct semantic equivalent (over `PanelRight*`):
// `PanelLeftOpen` (arrow pointing outward, panel expanding) maps to Carbon's
// `SidePanelOpen`, and `PanelLeftClose` (arrow pointing inward, panel
// collapsing) maps to Carbon's `SidePanelClose`.
export const SidePanelOpenIcon = featureFlags.dsTasklistUI ? LucidePanelLeftOpen : CarbonSidePanelOpen;
export const SidePanelCloseIcon = featureFlags.dsTasklistUI ? LucidePanelLeftClose : CarbonSidePanelClose;

// Share: generic "copy shareable link" affordance (the process-start-form
// modal's "Share process URL" action), not a Camunda domain entity — no
// registry match. Not in docs/kb/carbon-icons-to-lucide.md's mapping table,
// so this is the tier-3 fallback: Lucide's `Share2` (the node-and-lines
// glyph) is the closer visual match to Carbon's `Share` than Lucide's own
// `Share` (an iOS-style box-with-arrow glyph, a different concept).
export const ShareIcon = featureFlags.dsTasklistUI ? LucideShare2 : CarbonShare;
