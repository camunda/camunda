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
	type MultiSelectOption,
} from '@camunda/design-system';

type Props = {
	isOpen: boolean;
	owner: 'groups' | 'roles' | 'tenants';
	kind: string;
	entersIdAsText: boolean;
	candidates: {options: MultiSelectOption[]; isError: boolean; setSearch: (search: string) => void};
	isPending: boolean;
	onAssign: (ids: string[]) => Promise<{failed: {id: string}[]}>;
	onClose: () => void;
};

function getSortedOptionsIncludingSelected(options: MultiSelectOption[], selectedIds: string[]): MultiSelectOption[] {
	const selectedOptions = selectedIds
		.filter((id) => !options.some(({value}) => value === id))
		.map((id) => ({value: id, label: id}));
	return [...selectedOptions, ...options].sort((a, b) => a.value.localeCompare(b.value));
}

const AssignMembersModal: React.FC<Props> = ({
	isOpen,
	owner,
	kind,
	entersIdAsText,
	candidates,
	isPending,
	onAssign,
	onClose,
}) => {
	const {t} = useTranslation();
	const [selectedId, setSelectedId] = useState('');
	const [selectedIds, setSelectedIds] = useState<string[]>([]);
	const {options, isError: isCandidatesError, setSearch} = candidates;

	const key = `admin.${owner}.members.${kind}`;
	const title = t(`${key}.assign`);
	const idLabel = t(`${key}.idLabel`);
	const cancel = t(`admin.${owner}.cancel`);
	const inputId = `assign-${kind}-input`;

	const pickerOptions = getSortedOptionsIncludingSelected(options, selectedIds);
	const idsToAssign = entersIdAsText ? [selectedId.trim()].filter((id) => id !== '') : selectedIds;

	const closeUnlessPending = () => {
		if (!isPending) {
			onClose();
		}
	};

	const handleSubmit = async () => {
		if (idsToAssign.length === 0) {
			return;
		}
		const {failed} = await onAssign(idsToAssign);
		if (failed.length === 0) {
			onClose();
		} else {
			setSelectedIds(failed.map(({id}) => id));
		}
	};

	return (
		<Dialog
			open={isOpen}
			onOpenChange={(open) => {
				if (!open) {
					closeUnlessPending();
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
						aria-label={cancel}
						onClick={closeUnlessPending}
					>
						<X aria-hidden />
					</Button>
				</DialogHeader>
				<DialogBody>
					<form
						className="flex flex-col gap-1.5"
						onSubmit={(event) => {
							event.preventDefault();
							void handleSubmit();
						}}
					>
						<Label htmlFor={inputId}>{idLabel}</Label>
						{entersIdAsText ? (
							<Input
								id={inputId}
								autoFocus
								placeholder={t(`${key}.enterId`)}
								value={selectedId}
								onChange={(event) => setSelectedId(event.target.value)}
							/>
						) : (
							<MultiSelect
								id={inputId}
								className="w-full"
								aria-label={idLabel}
								externalFiltering
								options={pickerOptions}
								value={selectedIds}
								onValueChange={setSelectedIds}
								onInputChange={setSearch}
								placeholder={t(`${key}.select`)}
								searchPlaceholder={t(`${key}.search`)}
							/>
						)}
						{isCandidatesError && (
							<span role="alert" className="text-xs text-danger-foreground-subtle">
								{t(`admin.${owner}.members.candidatesLoadError`)}
							</span>
						)}
					</form>
				</DialogBody>
				<DialogFooter>
					<Button type="button" variant="secondary" onClick={closeUnlessPending}>
						{cancel}
					</Button>
					<Button
						type="button"
						loading={isPending}
						disabled={idsToAssign.length === 0}
						onClick={() => void handleSubmit()}
					>
						{title}
					</Button>
				</DialogFooter>
			</DialogContent>
		</Dialog>
	);
};

export {AssignMembersModal};
