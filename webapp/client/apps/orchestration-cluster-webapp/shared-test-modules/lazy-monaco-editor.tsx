/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {ComponentProps} from 'react';
import type {LazyMonacoEditor as RealLazyMonacoEditor} from '#/operate/shared/Editors/LazyMonacoEditor';

type Props = Pick<ComponentProps<typeof RealLazyMonacoEditor>, 'value' | 'readOnly' | 'onChange'>;

function LazyMonacoEditor({value, readOnly, onChange}: Props) {
	return (
		<textarea
			aria-label="Value"
			value={value}
			readOnly={readOnly}
			onChange={(event) => onChange?.(event.target.value)}
		/>
	);
}

export {LazyMonacoEditor};
