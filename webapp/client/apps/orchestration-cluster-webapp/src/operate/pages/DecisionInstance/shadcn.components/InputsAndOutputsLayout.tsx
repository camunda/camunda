/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMediaQuery} from '@camunda/design-system';
import {ResizablePanel, SplitDirection} from '#/operate/shared/ResizablePanel/shadcn.components/ResizablePanel';

type Props = {
	minWidths?: number[];
	children: React.ReactNode;
};

const InputsAndOutputsLayout: React.FC<Props> = ({minWidths, children}) => {
	const isNarrow = useMediaQuery('(width < 40rem)');

	if (isNarrow) {
		return <div className="flex h-full flex-col overflow-y-auto">{children}</div>;
	}

	return (
		<ResizablePanel
			panelId="decision-instance-horizontal-panel"
			direction={SplitDirection.Horizontal}
			minWidths={minWidths}
		>
			{children}
		</ResizablePanel>
	);
};

export {InputsAndOutputsLayout};
