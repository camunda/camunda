/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {Button} from '@carbon/react';
import {Maximize} from '@carbon/react/icons';

type Props = {
	onClick: () => void;
	isBusy?: boolean;
	ref?: React.Ref<HTMLButtonElement>;
};

const MaximizeButton: React.FC<Props> = ({onClick, isBusy, ref}) => {
	const {t} = useTranslation();

	return (
		<Button
			kind="ghost"
			hasIconOnly
			renderIcon={Maximize}
			size="sm"
			aria-label={t('operate.processInstance.variables.open')}
			iconDescription={t('operate.processInstance.variables.openDescription')}
			tooltipPosition="top"
			ref={ref}
			aria-busy={isBusy}
			onClick={onClick}
		/>
	);
};

export {MaximizeButton};
