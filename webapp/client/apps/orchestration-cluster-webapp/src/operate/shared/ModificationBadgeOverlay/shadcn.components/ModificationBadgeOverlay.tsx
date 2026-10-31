/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createPortal} from 'react-dom';
import {useTranslation} from 'react-i18next';
import {Badge} from '@camunda/design-system';
import {Minus, Plus} from '@camunda/design-system/icons';

type Props = {
	container: HTMLElement;
	newTokenCount: number;
	cancelledTokenCount: number;
};

const ModificationBadgeOverlay: React.FC<Props> = ({container, newTokenCount, cancelledTokenCount}) => {
	const {t} = useTranslation();

	if (newTokenCount === 0 && cancelledTokenCount === 0) {
		return null;
	}

	const label = [
		newTokenCount > 0 ? t('operate.shared.modificationBadge.added', {count: newTokenCount}) : undefined,
		cancelledTokenCount > 0 ? t('operate.shared.modificationBadge.canceled', {count: cancelledTokenCount}) : undefined,
	]
		.filter((part) => part !== undefined)
		.join(', ');

	return createPortal(
		<Badge
			data-testid="modifications-overlay"
			variant="info"
			role="img"
			title={label}
			className="-translate-x-1/2 gap-1 border-transparent bg-[var(--info-foreground-subtle)] pl-1 pr-2 align-top font-bold text-[var(--background)]"
		>
			{newTokenCount > 0 && (
				<>
					<Plus aria-hidden data-testid="badge-plus-icon" />
					{newTokenCount}
				</>
			)}
			{newTokenCount > 0 && cancelledTokenCount > 0 && ','}
			{cancelledTokenCount > 0 && (
				<>
					<Minus aria-hidden data-testid="badge-minus-icon" />
					{cancelledTokenCount}
				</>
			)}
		</Badge>,
		container,
	);
};

export {ModificationBadgeOverlay};
