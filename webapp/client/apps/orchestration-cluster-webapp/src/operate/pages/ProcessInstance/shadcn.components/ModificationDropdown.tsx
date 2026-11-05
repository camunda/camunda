/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {
	Button,
	Popover,
	PopoverAnchor,
	PopoverContent,
	PopoverHeader,
	PopoverTitle,
	Spinner,
} from '@camunda/design-system';
import {Ban, ArrowRight, Plus} from '@camunda/design-system/icons';
import {useModificationDropdown, type ModificationDropdownParams} from '../useModificationDropdown';

const ModificationDropdown: React.FC<ModificationDropdownParams> = (props) => {
	const {t} = useTranslation();
	const dropdown = useModificationDropdown(props);

	if (dropdown === undefined) {
		return null;
	}

	const {anchorRect, availableModifications, runningInstancesCount, hasResolvedInstance} = dropdown;
	const {top, left, width, height} = anchorRect;

	const content = (() => {
		if (dropdown.isFetchingElement) {
			return <Spinner />;
		}
		if (!dropdown.canBeModified) {
			return (
				<p className="text-sm text-muted-foreground">{t('operate.processInstance.modification.unsupportedElement')}</p>
			);
		}
		if (availableModifications.length === 0) {
			return (
				<p className="text-sm text-muted-foreground">{t('operate.processInstance.modification.noModifications')}</p>
			);
		}

		return (
			<>
				{runningInstancesCount > 0 && (
					<p className="text-sm">
						{t('operate.processInstance.modification.selectedRunningInstances', {count: runningInstancesCount})}
					</p>
				)}
				{runningInstancesCount > 1 && (
					<p className="text-sm text-muted-foreground">
						{t('operate.processInstance.modification.specificInstanceHint')}
					</p>
				)}
				<div className="flex flex-col items-stretch gap-1">
					{availableModifications.includes('add') && (
						<Button
							variant="ghost"
							size="sm"
							className="justify-start"
							title={t('operate.processInstance.modification.addLabel')}
							aria-label={t('operate.processInstance.modification.addLabel')}
							onClick={dropdown.addToken}
						>
							<Plus aria-hidden="true" data-icon="inline-start" />
							{t('operate.processInstance.modification.add')}
						</Button>
					)}
					{availableModifications.includes('cancel-instance') && hasResolvedInstance && (
						<Button
							variant="ghost"
							size="sm"
							className="justify-start"
							title={t('operate.processInstance.modification.cancelInstanceLabel')}
							aria-label={t('operate.processInstance.modification.cancelInstanceLabel')}
							onClick={dropdown.cancelInstance}
						>
							<Ban aria-hidden="true" data-icon="inline-start" />
							{t('operate.processInstance.modification.cancelInstance')}
						</Button>
					)}
					{availableModifications.includes('cancel-all') && (
						<Button
							variant="ghost"
							size="sm"
							className="justify-start"
							title={t('operate.processInstance.modification.cancelAllLabel')}
							aria-label={t('operate.processInstance.modification.cancelAllLabel')}
							onClick={dropdown.cancelAll}
						>
							<Ban aria-hidden="true" data-icon="inline-start" />
							{t('operate.processInstance.modification.cancelAll')}
						</Button>
					)}
					{availableModifications.includes('move-instance') && hasResolvedInstance && (
						<Button
							variant="ghost"
							size="sm"
							className="justify-start"
							title={t('operate.processInstance.modification.moveInstanceLabel')}
							aria-label={t('operate.processInstance.modification.moveInstanceLabel')}
							onClick={dropdown.moveInstance}
						>
							<ArrowRight aria-hidden="true" data-icon="inline-start" />
							{t('operate.processInstance.modification.moveInstance')}
						</Button>
					)}
					{availableModifications.includes('move-all') && (
						<Button
							variant="ghost"
							size="sm"
							className="justify-start"
							title={t('operate.processInstance.modification.moveAllLabel')}
							aria-label={t('operate.processInstance.modification.moveAllLabel')}
							onClick={dropdown.moveAll}
						>
							<ArrowRight aria-hidden="true" data-icon="inline-start" />
							{t('operate.processInstance.modification.moveAll')}
						</Button>
					)}
				</div>
			</>
		);
	})();

	return (
		<Popover open>
			<PopoverAnchor asChild>
				<span className="pointer-events-none fixed" style={{top, left, width, height}} />
			</PopoverAnchor>
			<PopoverContent
				side="bottom"
				aria-label={t('operate.processInstance.modification.elementModifications')}
				onOpenAutoFocus={(event) => event.preventDefault()}
				onInteractOutside={(event) => event.preventDefault()}
				onEscapeKeyDown={(event) => event.preventDefault()}
			>
				<PopoverHeader>
					<PopoverTitle>{t('operate.processInstance.modification.elementModifications')}</PopoverTitle>
				</PopoverHeader>
				<div className="flex flex-col gap-3">{content}</div>
			</PopoverContent>
		</Popover>
	);
};

export {ModificationDropdown};
