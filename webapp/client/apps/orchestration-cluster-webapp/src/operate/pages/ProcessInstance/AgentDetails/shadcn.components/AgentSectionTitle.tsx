/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Text} from '@camunda/design-system';

type AgentSectionTitleProps = {
	icon: React.ReactNode;
	children: React.ReactNode;
};

const AgentSectionTitle: React.FC<AgentSectionTitleProps> = ({icon, children}) => (
	<Text as="span" variant="heading-xs" className="flex items-center gap-3 text-foreground">
		<span className="text-[var(--neutral-foreground-subtle)] [&_svg]:size-4">{icon}</span>
		{children}
	</Text>
);

export {AgentSectionTitle};
