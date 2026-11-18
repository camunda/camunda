/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {Popover, PopoverContent, Stack} from '@carbon/react';
import {Add, ArrowRight, Error} from '@carbon/react/icons';
import {useModificationDropdown, type ModificationDropdownParams} from './useModificationDropdown';
import {
	DropdownAnchor,
	DropdownButton,
	DropdownContent,
	DropdownInfoMessage,
	DropdownInstanceCount,
	DropdownLoading,
	DropdownTitle,
	DropdownUnsupported,
} from './processInstanceModification.styled';

function ModificationDropdown(props: ModificationDropdownParams) {
	const {t} = useTranslation();
	const dropdown = useModificationDropdown(props);

	if (dropdown === undefined) {
		return null;
	}

	const {anchorRect, availableModifications, runningInstancesCount, hasResolvedInstance} = dropdown;
	const {top, left, width, height} = anchorRect;

	const content = (() => {
		if (dropdown.isFetchingElement) {
			return <DropdownLoading data-testid="dropdown-spinner" />;
		}
		if (!dropdown.canBeModified) {
			return <DropdownUnsupported>{t('operate.processInstance.modification.unsupportedElement')}</DropdownUnsupported>;
		}
		if (availableModifications.length === 0) {
			return <DropdownUnsupported>{t('operate.processInstance.modification.noModifications')}</DropdownUnsupported>;
		}

		return (
			<>
				{runningInstancesCount > 0 && (
					<DropdownInstanceCount>
						{t('operate.processInstance.modification.selectedRunningInstances', {count: runningInstancesCount})}
					</DropdownInstanceCount>
				)}
				{runningInstancesCount > 1 && (
					<DropdownInfoMessage>{t('operate.processInstance.modification.specificInstanceHint')}</DropdownInfoMessage>
				)}
				<Stack gap={2}>
					{availableModifications.includes('add') && (
						<DropdownButton
							kind="ghost"
							title={t('operate.processInstance.modification.addLabel')}
							aria-label={t('operate.processInstance.modification.addLabel')}
							size="sm"
							renderIcon={Add}
							onClick={dropdown.addToken}
						>
							{t('operate.processInstance.modification.add')}
						</DropdownButton>
					)}
					{availableModifications.includes('cancel-instance') && hasResolvedInstance && (
						<DropdownButton
							kind="ghost"
							title={t('operate.processInstance.modification.cancelInstanceLabel')}
							aria-label={t('operate.processInstance.modification.cancelInstanceLabel')}
							size="sm"
							renderIcon={Error}
							onClick={dropdown.cancelInstance}
						>
							{t('operate.processInstance.modification.cancelInstance')}
						</DropdownButton>
					)}
					{availableModifications.includes('cancel-all') && (
						<DropdownButton
							kind="ghost"
							title={t('operate.processInstance.modification.cancelAllLabel')}
							aria-label={t('operate.processInstance.modification.cancelAllLabel')}
							size="sm"
							renderIcon={Error}
							onClick={dropdown.cancelAll}
						>
							{t('operate.processInstance.modification.cancelAll')}
						</DropdownButton>
					)}
					{availableModifications.includes('move-instance') && hasResolvedInstance && (
						<DropdownButton
							kind="ghost"
							title={t('operate.processInstance.modification.moveInstanceLabel')}
							aria-label={t('operate.processInstance.modification.moveInstanceLabel')}
							size="sm"
							renderIcon={ArrowRight}
							onClick={dropdown.moveInstance}
						>
							{t('operate.processInstance.modification.moveInstance')}
						</DropdownButton>
					)}
					{availableModifications.includes('move-all') && (
						<DropdownButton
							kind="ghost"
							title={t('operate.processInstance.modification.moveAllLabel')}
							size="sm"
							renderIcon={ArrowRight}
							onClick={dropdown.moveAll}
						>
							{t('operate.processInstance.modification.moveAll')}
						</DropdownButton>
					)}
				</Stack>
			</>
		);
	})();

	return (
		<Popover open align="bottom" caret autoAlign>
			<DropdownAnchor style={{top, left, width, height}} />
			<PopoverContent>
				<DropdownContent data-testid="popover">
					<Stack gap={3}>
						<DropdownTitle>{t('operate.processInstance.modification.elementModifications')}</DropdownTitle>
						<Stack gap={4}>{content}</Stack>
					</Stack>
				</DropdownContent>
			</PopoverContent>
		</Popover>
	);
}

export {ModificationDropdown};
