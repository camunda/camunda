/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useLayoutEffect, useRef, useState} from 'react';
import {ResizablePanel, SplitDirection} from '#/operate/shared/ResizablePanel/shadcn.components/ResizablePanel';

const NARROW_WIDTH = 640;

type Props = {
	minWidths?: number[];
	children: React.ReactNode;
};

const InputsAndOutputsLayout: React.FC<Props> = ({minWidths, children}) => {
	const containerRef = useRef<HTMLDivElement>(null);
	const [isNarrow, setIsNarrow] = useState(false);

	useLayoutEffect(() => {
		const container = containerRef.current;
		if (container === null) {
			return;
		}

		const update = () => setIsNarrow(container.clientWidth < NARROW_WIDTH);
		update();
		const observer = new ResizeObserver(update);
		observer.observe(container);

		return () => observer.disconnect();
	}, []);

	return (
		<div ref={containerRef} className="h-full">
			{isNarrow ? (
				<div className="grid h-full grid-rows-[fit-content(50%)_minmax(0,1fr)] [&>section]:h-auto [&>section]:min-h-0">
					{children}
				</div>
			) : (
				<ResizablePanel
					panelId="decision-instance-horizontal-panel"
					direction={SplitDirection.Horizontal}
					minWidths={minWidths}
				>
					{children}
				</ResizablePanel>
			)}
		</div>
	);
};

export {InputsAndOutputsLayout};
