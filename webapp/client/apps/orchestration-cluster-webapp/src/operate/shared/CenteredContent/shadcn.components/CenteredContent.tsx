/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {cn} from '#/shared/cn';

type Props = {
	children: React.ReactNode;
	className?: string;
};

const CenteredContent: React.FC<Props> = ({children, className}) => (
	<div className={cn('flex h-full items-center justify-center text-center', className)}>{children}</div>
);

export {CenteredContent};
