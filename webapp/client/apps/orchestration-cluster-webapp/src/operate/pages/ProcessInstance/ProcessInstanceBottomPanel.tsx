/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useRef, useState} from 'react';
import {Link} from '@tanstack/react-router';
import {useTranslation} from 'react-i18next';
import {ResizablePanel, SplitDirection} from '#/operate/shared/ResizablePanel/ResizablePanel';
import {useMatchMedia, isWidthAboveBreakpoint} from '#/operate/shared/useMatchMedia';
import {useProcessInstancePage} from './useProcessInstancePage';
import {InstanceHistory} from './InstanceHistory';
import {ProcessInstanceDefaultTabRedirect} from './ProcessInstanceDefaultTabRedirect';
import {BottomPanel, TabPanel} from './instanceHistory.styled';
function ProcessInstanceBottomPanel({children}: {children: React.ReactNode}) {
	const {processInstanceId} = useProcessInstancePage();
	const {t} = useTranslation();
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
			<nav aria-label={t('operate.processInstance.history.tabs')}>
				{mobile && (
					<Link to="/operate/processes/$processInstanceId/history" params={{processInstanceId}} search={true}>
						{t('operate.processInstance.history.title')}
					</Link>
				)}
				{(['variables', 'incidents', 'details'] as const).map((tab) => (
					<Link
						key={tab}
						to={`/operate/processes/$processInstanceId/${tab}`}
						params={{processInstanceId}}
						search={true}
					>
						{t(`operate.processInstance.history.${tab}`)}
					</Link>
				))}
			</nav>
			<div>{children}</div>
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
