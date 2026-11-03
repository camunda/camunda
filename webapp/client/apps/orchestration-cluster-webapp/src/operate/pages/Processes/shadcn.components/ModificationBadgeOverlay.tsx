/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createPortal} from 'react-dom';
import {useTranslation} from 'react-i18next';
import {Text} from '@camunda/design-system';
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

	return createPortal(
		<Text
			as="span"
			variant="label-md-strong"
			role="img"
			aria-label={[
				newTokenCount > 0 ? t('operate.shared.modificationBadge.added', {count: newTokenCount}) : undefined,
				cancelledTokenCount > 0
					? t('operate.shared.modificationBadge.canceled', {count: cancelledTokenCount})
					: undefined,
			]
				.filter((part) => part !== undefined)
				.join(', ')}
			className="inline-flex -translate-x-1/2 items-center justify-center gap-1 rounded-full bg-primary-action-default px-3 py-1 text-primary-action-foreground"
		>
			{newTokenCount > 0 && (
				<>
					<Plus aria-hidden="true" className="size-4" />
					{newTokenCount}
				</>
			)}
			{newTokenCount > 0 && cancelledTokenCount > 0 && ','}
			{cancelledTokenCount > 0 && (
				<>
					<Minus aria-hidden="true" className="size-4" />
					{cancelledTokenCount}
				</>
			)}
		</Text>,
		container,
	);
};

export {ModificationBadgeOverlay};
