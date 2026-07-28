/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useSuspenseQuery} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {
	Select as DSSelect,
	SelectContent as DSSelectContent,
	SelectItem as DSSelectItem,
	SelectTrigger as DSSelectTrigger,
	SelectValue as DSSelectValue,
} from '@camunda/design-system';
import {Select as CompatSelect, SelectItem as CompatSelectItem} from '#/shared/design-system-compat';
import {featureFlags} from '#/shared/feature-flags';
import {queries} from '#/shared/http/queries';
import {cn} from '#/shared/cn';

const DEFAULT_TENANT_ID = '<default>';

type Props = {
	tenantId?: string;
	id?: string;
	name?: string;
	value?: string;
	className?: string;
	labelText?: React.ReactNode;
	hideLabel?: boolean;
	onChange?: (event: {target: {value: string; id?: string}}) => void;
	onBlur?: (event?: React.FocusEvent<HTMLElement>) => void;
	onFocus?: (event?: React.FocusEvent<HTMLElement>) => void;
};

type ProcessOption = {value: string; label: string};

function useProcessOptions(tenantId: string) {
	return useSuspenseQuery({
		...queries.queryProcessDefinitions({
			filter: {isLatestVersion: true, tenantId},
			page: {limit: 1000},
		}),
		select({items}): ProcessOption[] {
			return items.map(({processDefinitionKey, name, processDefinitionId, version}) => ({
				value: processDefinitionKey,
				label: `${name ?? processDefinitionId} - v${version}`,
			}));
		},
	}).data;
}

// Uses the DS's raw Select primitives instead of the design-system-compat wrapper:
// carbon-compat's Select/Dropdown don't forward Radix's avoidCollisions/side props
// (confirmed by reading carbon-compat/select.tsx and dropdown.tsx — both use a fixed
// prop allowlist with no passthrough to SelectContent), so there's no way to stop
// this dropdown from flipping above its trigger through the compat layer. Reported
// as a compat-layer gap; this is the scoped workaround for this one field. Bypassing
// the compat layer here is exactly why this needs its own explicit feature-flag
// branch below, same as LabelWithPopover.
const ProcessesSelectDS: React.FC<Props> = ({
	tenantId = DEFAULT_TENANT_ID,
	id,
	name,
	value,
	className,
	labelText,
	hideLabel,
	onChange,
	onBlur,
	onFocus,
}) => {
	const {t} = useTranslation();
	const processes = useProcessOptions(tenantId);
	const allProcessesLabel = t('tasklist.customFiltersModalAllProcesses');
	// prepareCustomFiltersParams.ts treats '' (the field's unset default) and 'all'
	// as equivalent — both mean "no process filter applied". Displaying 'all' as the
	// selected value here (rather than leaving it undefined) is a display-only
	// normalization so the "All processes" item shows its checkmark by default; it
	// doesn't call onChange, so the underlying form value is untouched until the
	// user actually picks something.
	const resolvedValue = value === '' || value === undefined ? 'all' : value;

	return (
		<div className="flex flex-col gap-1">
			{labelText !== undefined ? (
				<label htmlFor={id} className={cn('text-sm font-medium', hideLabel && 'sr-only')}>
					{labelText}
				</label>
			) : null}
			<DSSelect value={resolvedValue} onValueChange={(newValue) => onChange?.({target: {value: newValue, id}})}>
				<DSSelectTrigger id={id} name={name} onBlur={onBlur} onFocus={onFocus} className={cn('w-full', className)}>
					<DSSelectValue placeholder={allProcessesLabel} />
				</DSSelectTrigger>
				<DSSelectContent avoidCollisions={false} side="bottom">
					<DSSelectItem value="all">{allProcessesLabel}</DSSelectItem>
					{processes.map(({value: processValue, label}) => (
						<DSSelectItem key={processValue} value={processValue}>
							{label}
						</DSSelectItem>
					))}
				</DSSelectContent>
			</DSSelect>
		</div>
	);
};

// Original implementation, unchanged from before this session — routes through
// the design-system-compat Select/SelectItem, which resolve to Carbon's real
// components with the flag off. Carbon's Select doesn't destructure name/onBlur/
// onFocus (confirmed by reading carbon-compat/select.tsx's exact prop list) and
// doesn't spread ...rest onto anything either, so those three props were already
// silently unused via this path before this session — not something introduced
// here, just preserved exactly as it was.
const ProcessesSelectLegacy: React.FC<Props> = ({
	tenantId = DEFAULT_TENANT_ID,
	id,
	value,
	className,
	labelText,
	hideLabel,
	onChange,
}) => {
	const {t} = useTranslation();
	const processes = useProcessOptions(tenantId);
	const allProcessesLabel = t('tasklist.customFiltersModalAllProcesses');

	return (
		<CompatSelect
			id={id as string}
			value={value}
			onChange={onChange}
			className={className}
			labelText={labelText}
			hideLabel={hideLabel}
			placeholder={allProcessesLabel}
		>
			<CompatSelectItem value="all" text={allProcessesLabel} />
			{processes.map(({value: processValue, label}) => (
				<CompatSelectItem key={processValue} value={processValue} text={label} />
			))}
		</CompatSelect>
	);
};

const ProcessesSelect: React.FC<Props> = (props) =>
	featureFlags.dsTasklistUI ? <ProcessesSelectDS {...props} /> : <ProcessesSelectLegacy {...props} />;

export {ProcessesSelect};
