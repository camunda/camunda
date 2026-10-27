/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useRef, useState} from 'react';
import {ResizablePanel, SplitDirection} from '#/operate/shared/ResizablePanel/ResizablePanel';
import {useMatchMedia, isWidthAboveBreakpoint} from '#/operate/shared/useMatchMedia';
import {InstanceHistory} from './InstanceHistory';
import {ProcessInstanceDefaultTabRedirect} from './ProcessInstanceDefaultTabRedirect';
import {ProcessInstanceTabs} from './ProcessInstanceTabs';
import {BottomPanel, TabPanel} from './instanceHistory.styled';
import {TabContent} from './processInstanceTabs.styled';
function ProcessInstanceBottomPanel({children}: {children: React.ReactNode}) {
	const mobile = !useMatchMedia(isWidthAboveBreakpoint('lg'));
	const ref = useRef<HTMLDivElement>(null);
	const [width, setWidth] = useState(0);
	useEffect(() => {
		const node = ref.current;
		if (!node) {
			return;
		}
		const observer = new ResizeObserver(([entry]) => setWidth(entry?.contentRect.width ?? 0));
		observer.observe(node);
		return () => observer.disconnect();
	}, []);
	const tabs = (
		<TabPanel>
			<ProcessInstanceTabs isHistoryTabVisible={mobile} />
			<TabContent>{children}</TabContent>
		</TabPanel>
	);
	return (
		<BottomPanel ref={ref}>
			{mobile ? (
				tabs
			) : (
				<ResizablePanel
					panelId="process-instance-bottom-panel"
					direction={SplitDirection.Horizontal}
					minWidths={[Math.max(420, width / 4), width / 4]}
				>
					<InstanceHistory />
					{tabs}
				</ResizablePanel>
			)}
		</BottomPanel>
	);
}
function ProcessInstanceHistoryTab() {
	const mobile = !useMatchMedia(isWidthAboveBreakpoint('lg'));
	return mobile ? <InstanceHistory showHeader={false} /> : <ProcessInstanceDefaultTabRedirect />;
}
export {ProcessInstanceBottomPanel, ProcessInstanceHistoryTab};
