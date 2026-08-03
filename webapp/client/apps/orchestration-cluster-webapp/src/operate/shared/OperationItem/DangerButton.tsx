/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {Button, type ButtonSize} from '@carbon/react';

type ItemProps = {
	type: 'DELETE';
	onClick: () => void;
	title: string;
	disabled?: boolean;
	size?: ButtonSize;
};

const TYPE_DETAILS: Readonly<Record<ItemProps['type'], {testId: string}>> = {
	DELETE: {testId: 'delete-operation'},
};

const DangerButton: React.FC<ItemProps> = ({title, onClick, type, disabled, size}) => {
	const {t} = useTranslation();
	const {testId} = TYPE_DETAILS[type];

	return (
		<li>
			<Button
				kind="danger--ghost"
				iconDescription={title}
				onClick={onClick}
				disabled={disabled}
				data-testid={testId}
				title={title}
				aria-label={title}
				size={size}
			>
				{t('operate.shared.operations.deleteButtonLabel')}
			</Button>
		</li>
	);
};

export {DangerButton};
