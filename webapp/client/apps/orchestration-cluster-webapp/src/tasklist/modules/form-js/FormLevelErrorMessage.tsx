/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {CircleAlert} from '@camunda/design-system/icons';

type Props = {
	screenReaderMessage?: string;
	readableMessage: string;
};

const FormLevelErrorMessage: React.FC<Props> = ({screenReaderMessage, readableMessage}) => {
	return (
		<div className="flex items-start gap-2" role="alert" aria-label={screenReaderMessage}>
			<CircleAlert className="size-4 shrink-0 text-danger-action-default" aria-hidden />
			<div aria-hidden={screenReaderMessage !== undefined}>{readableMessage}</div>
		</div>
	);
};

export {FormLevelErrorMessage};
