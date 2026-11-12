/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {Alert} from '@camunda/design-system';

type Props = {
	variant: 'destructive' | 'warning';
	title: string;
};

const DefinitionAlert: React.FC<Props> = ({variant, title}) => {
	const [isDismissed, setIsDismissed] = useState(false);

	if (isDismissed) {
		return null;
	}

	return (
		<Alert
			variant={variant}
			role="status"
			aria-live="polite"
			title={title}
			dismissible
			onDismiss={() => setIsDismissed(true)}
			className="max-w-[608px] min-[1056px]:max-w-[736px] min-[1584px]:max-w-[832px]"
		/>
	);
};

export {DefinitionAlert};
