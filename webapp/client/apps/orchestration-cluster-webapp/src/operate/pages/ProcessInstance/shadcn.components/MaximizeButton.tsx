/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {IconButton} from '@camunda/design-system';
import {Maximize} from '@camunda/design-system/icons';

type Props = {
	onClick: () => void;
	isBusy?: boolean;
	ref?: React.Ref<HTMLButtonElement>;
};

const MaximizeButton: React.FC<Props> = ({onClick, isBusy, ref}) => {
	const {t} = useTranslation();

	return (
		<IconButton
			type="button"
			variant="ghost"
			size="sm"
			icon={Maximize}
			label={t('operate.processInstance.variables.openDescription')}
			tooltipSide="top"
			ref={ref}
			aria-busy={isBusy}
			onClick={onClick}
		/>
	);
};

export {MaximizeButton};
