/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {Button, type ButtonSize} from '@carbon/react';
import {TrashCan, type CarbonIconType} from '@carbon/react/icons';

type ItemProps = {
	type: 'DELETE';
	onClick: () => void;
	title: string;
	disabled?: boolean;
	isIconOnly?: boolean;
	size?: ButtonSize;
};

const TYPE_DETAILS: Readonly<Record<ItemProps['type'], {icon: CarbonIconType; testId: string}>> = {
	DELETE: {icon: TrashCan, testId: 'delete-operation'},
};

const DangerButton: React.FC<ItemProps> = ({title, onClick, type, disabled, isIconOnly = false, size}) => {
	const {t} = useTranslation();
	const {icon, testId} = TYPE_DETAILS[type];
	const buttonProps = {
		kind: 'danger--ghost',
		iconDescription: title,
		onClick,
		disabled,
		'data-testid': testId,
		'aria-label': title,
		size,
	} satisfies React.ComponentProps<typeof Button>;

	return (
		<li>
			{isIconOnly ? (
				<Button {...buttonProps} renderIcon={icon} tooltipPosition="left" hasIconOnly />
			) : (
				<Button {...buttonProps} title={title}>
					{t('operate.shared.operations.deleteButtonLabel')}
				</Button>
			)}
		</li>
	);
};

export {DangerButton};
