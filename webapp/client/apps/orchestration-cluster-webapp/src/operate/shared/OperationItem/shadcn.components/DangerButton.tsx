/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Button, IconButton} from '@camunda/design-system';
import {Trash2} from '@camunda/design-system/icons';
import {useTranslation} from 'react-i18next';

type ItemProps = {
	type: 'DELETE';
	onClick: () => void;
	title: string;
	disabled?: boolean;
	isIconOnly?: boolean;
	size?: NonNullable<React.ComponentProps<typeof Button>['size']> & React.ComponentProps<typeof IconButton>['size'];
};

const TYPE_DETAILS: Readonly<Record<ItemProps['type'], {testId: string}>> = {
	DELETE: {testId: 'delete-operation'},
};

const DangerButton: React.FC<ItemProps> = ({title, onClick, type, disabled, isIconOnly = false, size = 'default'}) => {
	const {t} = useTranslation();
	const {testId} = TYPE_DETAILS[type];

	return (
		<li>
			{isIconOnly ? (
				<IconButton
					icon={Trash2}
					label={title}
					variant="destructive"
					size={size}
					tooltipSide="left"
					onClick={onClick}
					disabled={disabled}
					data-testid={testId}
				/>
			) : (
				<Button
					variant="destructive"
					size={size}
					onClick={onClick}
					disabled={disabled}
					data-testid={testId}
					aria-label={title}
				>
					{t('operate.shared.operations.deleteButtonLabel')}
				</Button>
			)}
		</li>
	);
};

export {DangerButton};
