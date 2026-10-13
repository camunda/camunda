/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {useQuery} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {
	Button,
	Dialog,
	DialogBody,
	DialogContent,
	DialogFooter,
	DialogHeader,
	DialogTitle,
	Input,
	Label,
	MultiSelect,
	type MultiSelectOption,
} from '@camunda/design-system';
import {queries} from '#/shared/http/queries';
import type {TenantTab} from './detailSearchSchema';
import {createMember, getMemberId, useTenantMemberMutations, type TenantMember} from './useTenantMemberMutations';

const OPTIONS_LIMIT = 50;

type Props = {
	tab: TenantTab;
	tenantId: string;
	isOpen: boolean;
	isFreeText: boolean;
	assignedIds: string[];
	onClose: () => void;
};

const AssignMembersModal: React.FC<Props> = ({tab, tenantId, isOpen, isFreeText, assignedIds, onClose}) => {
	const {t} = useTranslation();
	const {assign} = useTenantMemberMutations(tab, tenantId);
	const [selected, setSelected] = useState<string[]>([]);
	const [knownMembers, setKnownMembers] = useState<Record<string, TenantMember>>({});
	const [freeText, setFreeText] = useState('');
	const [term, setTerm] = useState('');

	const like = term === '' ? undefined : {$like: `*${term}*`};
	const page = {limit: OPTIONS_LIMIT};
	const isPicker = isOpen && !isFreeText;

	const users = useQuery({
		...queries.queryUsers({filter: like ? {username: like} : {}, page}),
		enabled: isPicker && tab === 'users',
	});
	const groups = useQuery({
		...queries.queryGroups({filter: like ? {groupId: like} : {}, page}),
		enabled: isPicker && tab === 'groups',
	});
	const roles = useQuery({
		...queries.queryRoles({filter: like ? {roleId: like} : {}, page}),
		enabled: isPicker && tab === 'roles',
	});
	const mappingRules = useQuery({
		...queries.queryMappingRules({filter: like ? {mappingRuleId: like} : {}, page}),
		enabled: isPicker && tab === 'mapping-rules',
	});

	const candidates: TenantMember[] =
		(tab === 'users'
			? users.data?.items
			: tab === 'groups'
				? groups.data?.items
				: tab === 'roles'
					? roles.data?.items
					: tab === 'mapping-rules'
						? mappingRules.data?.items
						: undefined) ?? [];
	const candidateIds = candidates.map((candidate) => getMemberId(tab, candidate));

	const options: MultiSelectOption[] = [...new Set([...selected, ...candidateIds])]
		.filter((id) => selected.includes(id) || !assignedIds.includes(id))
		.map((id) => ({value: id, label: id}));

	const ids = isFreeText ? [freeText.trim()].filter((id) => id !== '') : selected;

	const select = (next: string[]) => {
		setSelected(next);
		setKnownMembers((known) => ({
			...known,
			...Object.fromEntries(
				candidates
					.map((candidate) => [getMemberId(tab, candidate), candidate] as const)
					.filter(([id]) => next.includes(id)),
			),
		}));
	};

	const close = () => {
		setSelected([]);
		setKnownMembers({});
		setFreeText('');
		setTerm('');
		onClose();
	};

	return (
		<Dialog
			open={isOpen}
			onOpenChange={(open) => {
				if (!open) {
					close();
				}
			}}
		>
			<DialogContent
				size="sm"
				showCloseButton={false}
				aria-label={t(`admin.tenants.assignModalTitle_${tab}`)}
				aria-describedby={undefined}
				onInteractOutside={(event) => event.preventDefault()}
			>
				<form
					onSubmit={(event) => {
						event.preventDefault();
						assign.mutate(
							ids.map((id) => knownMembers[id] ?? createMember(tab, id)),
							{onSuccess: close},
						);
					}}
				>
					<DialogHeader>
						<DialogTitle>{t(`admin.tenants.assignModalTitle_${tab}`)}</DialogTitle>
					</DialogHeader>
					<DialogBody>
						<div className="flex flex-col gap-1.5">
							<Label htmlFor="tenant-member-field" id="tenant-member-label">
								{t(`admin.tenants.assignFieldLabel_${tab}`)}
							</Label>
							{isFreeText ? (
								<Input
									id="tenant-member-field"
									value={freeText}
									onChange={(event) => setFreeText(event.target.value)}
								/>
							) : (
								<MultiSelect
									id="tenant-member-field"
									aria-labelledby="tenant-member-label"
									placeholder={t(`admin.tenants.assignFieldPlaceholder_${tab}`)}
									options={options}
									value={selected}
									onValueChange={select}
									onInputChange={setTerm}
									externalFiltering
									hideSelectAll
									maxCount={OPTIONS_LIMIT}
								/>
							)}
						</div>
					</DialogBody>
					<DialogFooter>
						<Button type="button" variant="secondary" onClick={close} disabled={assign.isPending}>
							{t('admin.tenants.cancelButton')}
						</Button>
						<Button type="submit" disabled={ids.length === 0 || assign.isPending}>
							{t('admin.tenants.assignButton')}
						</Button>
					</DialogFooter>
				</form>
			</DialogContent>
		</Dialog>
	);
};

export {AssignMembersModal};
