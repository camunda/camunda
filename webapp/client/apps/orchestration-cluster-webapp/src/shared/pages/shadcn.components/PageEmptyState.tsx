/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {EmptyState} from '@camunda/design-system';

type Props = React.ComponentProps<typeof EmptyState>;

const PageEmptyState: React.FC<Props> = (props) => (
	<div className="flex min-h-full items-center justify-center bg-background p-4">
		<EmptyState headingLevel={1} {...props} />
	</div>
);

export {PageEmptyState};
