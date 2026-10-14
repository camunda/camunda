/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {useTranslation} from 'react-i18next';
import {X} from '@camunda/design-system/icons';
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
} from '@camunda/design-system';
import type {MemberKind} from './members';
import {useGroupMemberMutations} from './useGroupMemberMutations';
import {useMemberCandidates} from './useMemberCandidates';

type Props = {
	isOpen: boolean;
	groupId: string;
	kind: MemberKind;
	isOidc: boolean;
	assignedIds: string[];
	onClose: () => void;
};

const AssignMemberModal: React.FC<Props> = ({isOpen, groupId, kind, isOidc, assignedIds, onClose}) => {
	const {t} = useTranslation();
	const {assign} = useGroupMemberMutations(groupId);
	const [selectedId, setSelectedId] = useState('');
	const [selectedIds, setSelectedIds] = useState<string[]>([]);
	const {options, isError: isCandidatesError, setSearch} = useMemberCandidates(kind, isOidc, assignedIds);

	const entersIdAsText = kind === 'clients' || (kind === 'users' && isOidc);
	const title = t(`admin.groups.members.${kind}.assign`);
	const inputId = `assign-${kind}-input`;

	// Selected members stay listed while a search narrows the candidates, so their chips keep a label.
	const pickerOptions = [
		...selectedIds.filter((id) => !options.some(({value}) => value === id)).map((id) => ({value: id, label: id})),
		...options,
	];
	const idsToAssign = entersIdAsText ? [selectedId.trim()].filter((id) => id !== '') : selectedIds;

	const handleSubmit = () => {
		if (idsToAssign.length === 0) {
			return;
		}
		assign.mutate(
			{kind, ids: idsToAssign},
			{
				onSuccess: ({failed}) => {
					if (failed.length === 0) {
						onClose();
					} else {
						setSelectedIds(failed.map(({id}) => id));
					}
				},
			},
		);
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
						aria-label={t('admin.groups.cancel')}
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
						<Label htmlFor={inputId}>{t(`admin.groups.members.${kind}.idLabel`)}</Label>
						{entersIdAsText ? (
							<Input
								id={inputId}
								autoFocus
								placeholder={t(`admin.groups.members.${kind}.enterId`)}
								value={selectedId}
								onChange={(event) => setSelectedId(event.target.value)}
							/>
						) : (
							<MultiSelect
								id={inputId}
								className="w-full"
								aria-label={t(`admin.groups.members.${kind}.idLabel`)}
								externalFiltering
								options={pickerOptions}
								value={selectedIds}
								onValueChange={setSelectedIds}
								onInputChange={setSearch}
								placeholder={t(`admin.groups.members.${kind}.select`)}
								searchPlaceholder={t(`admin.groups.members.${kind}.search`)}
							/>
						)}
						{isCandidatesError && (
							<span role="alert" className="text-xs text-danger-foreground-subtle">
								{t('admin.groups.members.candidatesLoadError')}
							</span>
						)}
					</form>
				</DialogBody>
				<DialogFooter>
					<Button type="button" variant="secondary" onClick={onClose}>
						{t('admin.groups.cancel')}
					</Button>
					<Button type="button" loading={assign.isPending} disabled={idsToAssign.length === 0} onClick={handleSubmit}>
						{title}
					</Button>
				</DialogFooter>
			</DialogContent>
		</Dialog>
	);
};

export {AssignMemberModal};
