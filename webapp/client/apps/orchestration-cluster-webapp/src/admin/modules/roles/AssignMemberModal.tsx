/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useState} from 'react';
import {useQuery} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {X} from '@camunda/design-system/icons';
import {
	Button,
	Combobox,
	Dialog,
	DialogBody,
	DialogContent,
	DialogFooter,
	DialogHeader,
	DialogTitle,
	Input,
	Label,
	type ComboboxOption,
} from '@camunda/design-system';
import type {MemberKind} from './members';
import {useRoleMemberMutations} from './useRoleMemberMutations';
import {queries} from '#/shared/http/queries';

const CANDIDATE_LIMIT = 20;
const CANDIDATE_SEARCH_DEBOUNCE = 300;

type Candidates = {options: ComboboxOption[]; isError: boolean};

function useCandidates(
	kind: MemberKind,
	isOidc: boolean,
	isCamundaGroupsEnabled: boolean,
	searchTerm: string,
): Candidates {
	const filterValue = searchTerm === '' ? undefined : {$like: `*${searchTerm}*`};
	const page = {from: 0, limit: CANDIDATE_LIMIT};

	const users = useQuery({
		...queries.queryUsers({filter: filterValue ? {username: filterValue} : {}, page}),
		enabled: kind === 'users' && !isOidc,
	});
	const groups = useQuery({
		...queries.queryGroups({filter: filterValue ? {groupId: filterValue} : {}, page}),
		enabled: kind === 'groups' && isCamundaGroupsEnabled,
	});
	const mappingRules = useQuery({
		...queries.queryMappingRules({filter: filterValue ? {mappingRuleId: filterValue} : {}, page}),
		enabled: kind === 'mappingRules',
	});

	switch (kind) {
		case 'users':
			return {
				options: (users.data?.items ?? []).map(({username}) => ({value: username, label: username})),
				isError: users.isError,
			};
		case 'groups':
			return {
				options: (groups.data?.items ?? []).map(({groupId}) => ({value: groupId, label: groupId})),
				isError: groups.isError,
			};
		case 'mappingRules':
			return {
				options: (mappingRules.data?.items ?? []).map(({mappingRuleId}) => ({
					value: mappingRuleId,
					label: mappingRuleId,
				})),
				isError: mappingRules.isError,
			};
		case 'clients':
			return {options: [], isError: false};
	}
}

type Props = {
	isOpen: boolean;
	roleId: string;
	kind: MemberKind;
	isOidc: boolean;
	isCamundaGroupsEnabled: boolean;
	onClose: () => void;
};

const AssignMemberModal: React.FC<Props> = ({isOpen, roleId, kind, isOidc, isCamundaGroupsEnabled, onClose}) => {
	const {t} = useTranslation();
	const {assign} = useRoleMemberMutations(roleId);
	const [selectedId, setSelectedId] = useState('');
	const [searchDraft, setSearchDraft] = useState('');
	const [searchTerm, setSearchTerm] = useState('');
	const {options, isError: isCandidatesError} = useCandidates(kind, isOidc, isCamundaGroupsEnabled, searchTerm);
	// Delegated logins keep their users in the identity provider, and groups are only managed here when
	// Camunda groups are enabled, so there is no local list to pick from in those cases.
	const entersIdAsText =
		kind === 'clients' || (kind === 'users' && isOidc) || (kind === 'groups' && !isCamundaGroupsEnabled);
	const title = t(`admin.roles.members.${kind}.assign`);
	const inputId = `assign-${kind}-input`;

	useEffect(() => {
		const timeoutId = setTimeout(() => setSearchTerm(searchDraft), CANDIDATE_SEARCH_DEBOUNCE);
		return () => clearTimeout(timeoutId);
	}, [searchDraft]);

	const handleSubmit = () => {
		const id = selectedId.trim();
		if (id === '') {
			return;
		}
		assign.mutate({kind, id}, {onSuccess: onClose});
	};

	return (
		<Dialog
			open={isOpen}
			onOpenChange={(open) => {
				if (!open) {
					onClose();
				}
			}}
		>
			<DialogContent
				size="sm"
				showCloseButton={false}
				aria-label={title}
				aria-describedby={undefined}
				onInteractOutside={(event) => event.preventDefault()}
			>
				<DialogHeader>
					<DialogTitle>{title}</DialogTitle>
					<Button
						type="button"
						variant="ghost"
						size="icon-sm"
						className="absolute top-2 right-2"
						aria-label={t('admin.roles.cancel')}
						onClick={onClose}
					>
						<X aria-hidden />
					</Button>
				</DialogHeader>
				<DialogBody>
					<form
						className="flex flex-col gap-1.5"
						onSubmit={(event) => {
							event.preventDefault();
							handleSubmit();
						}}
					>
						<Label htmlFor={inputId}>{t(`admin.roles.members.${kind}.idLabel`)}</Label>
						{entersIdAsText ? (
							<Input
								id={inputId}
								autoFocus
								placeholder={t(`admin.roles.members.${kind}.enterId`)}
								value={selectedId}
								onChange={(event) => setSelectedId(event.target.value)}
							/>
						) : (
							<Combobox
								id={inputId}
								externalFiltering
								options={options}
								value={selectedId === '' ? undefined : selectedId}
								onValueChange={(value) => setSelectedId(value ?? '')}
								onInputChange={setSearchDraft}
								placeholder={t(`admin.roles.members.${kind}.select`)}
								searchPlaceholder={t(`admin.roles.members.${kind}.search`)}
							/>
						)}
						{isCandidatesError && (
							<span role="alert" className="text-xs text-danger-foreground-subtle">
								{t('admin.roles.members.candidatesLoadError')}
							</span>
						)}
					</form>
				</DialogBody>
				<DialogFooter>
					<Button type="button" variant="secondary" onClick={onClose}>
						{t('admin.roles.cancel')}
					</Button>
					<Button type="button" loading={assign.isPending} disabled={selectedId.trim() === ''} onClick={handleSubmit}>
						{title}
					</Button>
				</DialogFooter>
			</DialogContent>
		</Dialog>
	);
};

export {AssignMemberModal};
