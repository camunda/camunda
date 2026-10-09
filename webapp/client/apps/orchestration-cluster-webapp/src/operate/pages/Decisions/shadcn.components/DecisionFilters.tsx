/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {useNavigate} from '@tanstack/react-router';
import {
	Checkbox,
	Combobox,
	Heading,
	Label,
	Select,
	SelectContent,
	SelectItem,
	SelectTrigger,
	SelectValue,
} from '@camunda/design-system';
import {getClientConfig} from '#/shared/config/getClientConfig';
import {TenantField} from '#/operate/shared/TenantField/shadcn.components/TenantField';
import {StateIcon} from '#/operate/shared/StateIcon/shadcn.components/StateIcon';
import type {DecisionsSearch} from '../decisionsFilter';
import {useDecisionFilterOptions} from './useDecisionFilterOptions';

const ALL_VERSIONS = 'all';

type Props = Pick<
	DecisionsSearch,
	'decisionDefinitionId' | 'decisionDefinitionVersion' | 'tenantId' | 'evaluated' | 'failed'
>;

const DecisionFilters: React.FC<Props> = ({
	decisionDefinitionId,
	decisionDefinitionVersion,
	tenantId,
	evaluated,
	failed,
}) => {
	const {t} = useTranslation();
	const navigate = useNavigate();
	const {decisionOptions, versions} = useDecisionFilterOptions({
		decisionDefinitionId,
		decisionDefinitionVersion,
		tenantId,
	});
	const selectedVersion =
		decisionDefinitionId === undefined
			? ''
			: decisionDefinitionVersion === undefined
				? ALL_VERSIONS
				: String(decisionDefinitionVersion);

	return (
		<div className="flex flex-col gap-8">
			<div className="flex flex-col gap-5">
				{getClientConfig().deployment.isMultiTenancyEnabled && (
					<section className="flex flex-col gap-3">
						<Heading as="h3" variant="heading-xs">
							{t('operate.decisions.filters.tenant')}
						</Heading>
						<TenantField
							onChange={() => {
								void navigate({
									to: '.',
									search: (prev) => ({
										...prev,
										decisionDefinitionId: undefined,
										decisionDefinitionVersion: undefined,
									}),
								});
							}}
						/>
					</section>
				)}
				<section className="flex flex-col gap-3">
					<Heading as="h3" variant="heading-xs">
						{t('operate.decisions.filters.decisionSection')}
					</Heading>
					<div className="flex flex-col gap-1.5">
						<Label htmlFor="decision-name-filter">{t('operate.decisions.filters.name')}</Label>
						<Combobox
							id="decision-name-filter"
							className="w-full"
							size="sm"
							options={decisionOptions}
							value={decisionDefinitionId}
							placeholder={t('operate.decisions.filters.searchByName')}
							searchPlaceholder={t('operate.decisions.filters.searchByName')}
							onValueChange={(value) => {
								void navigate({
									to: '.',
									search: (prev) => ({
										...prev,
										decisionDefinitionId: value,
										decisionDefinitionVersion: value === decisionDefinitionId ? decisionDefinitionVersion : undefined,
									}),
								});
							}}
						/>
					</div>
					<div className="flex flex-col gap-1.5">
						<Label htmlFor="decision-version-filter">{t('operate.decisions.filters.version')}</Label>
						<Select
							value={selectedVersion}
							disabled={!decisionDefinitionId}
							onValueChange={(value) => {
								void navigate({
									to: '.',
									search: (prev) => ({
										...prev,
										decisionDefinitionVersion: value === ALL_VERSIONS ? undefined : Number(value),
									}),
								});
							}}
						>
							<SelectTrigger id="decision-version-filter" size="sm" className="w-full">
								<SelectValue placeholder={t('operate.decisions.filters.selectVersion')} />
							</SelectTrigger>
							<SelectContent>
								<SelectItem value={ALL_VERSIONS}>{t('operate.decisions.filters.allVersions')}</SelectItem>
								{versions.map((version) => (
									<SelectItem key={version} value={String(version)}>
										{version}
									</SelectItem>
								))}
							</SelectContent>
						</Select>
					</div>
				</section>
			</div>
			<section className="flex flex-col gap-3">
				<Heading as="h3" variant="heading-xs">
					{t('operate.decisions.filters.instancesStates')}
				</Heading>
				<div className="flex flex-col gap-2">
					<Label htmlFor="filter-evaluated" className="cursor-pointer font-normal">
						<Checkbox
							id="filter-evaluated"
							checked={evaluated}
							onCheckedChange={(checked) => {
								void navigate({to: '.', search: (prev) => ({...prev, evaluated: checked === true})});
							}}
						/>
						<StateIcon state="EVALUATED" size={16} />
						{t('operate.decisions.filters.evaluated')}
					</Label>
					<Label htmlFor="filter-failed" className="cursor-pointer font-normal">
						<Checkbox
							id="filter-failed"
							checked={failed}
							onCheckedChange={(checked) => {
								void navigate({to: '.', search: (prev) => ({...prev, failed: checked === true})});
							}}
						/>
						<StateIcon state="FAILED" size={16} />
						{t('operate.decisions.filters.failed')}
					</Label>
				</div>
			</section>
		</div>
	);
};

export {DecisionFilters};
