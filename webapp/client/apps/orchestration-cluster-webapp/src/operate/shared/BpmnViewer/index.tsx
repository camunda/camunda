/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import 'bpmn-js/dist/assets/bpmn-js.css';
import {useEffect, useRef} from 'react';
import NavigatedViewer from 'bpmn-js/lib/NavigatedViewer';
import styled from 'styled-components';

const Container = styled.div`
	width: 100%;
	height: 100%;
`;

type Props = {
	xml: string;
};

function BpmnViewer({xml}: Props) {
	const containerRef = useRef<HTMLDivElement>(null);
	const viewerRef = useRef<InstanceType<typeof NavigatedViewer> | null>(null);

	useEffect(() => {
		const container = containerRef.current;
		if (!container) {
			return;
		}

		const viewer = new NavigatedViewer({container});
		viewerRef.current = viewer;

		return () => {
			viewer.destroy();
			viewerRef.current = null;
		};
	}, []);

	useEffect(() => {
		viewerRef.current?.importXML(xml).catch(() => {});
	}, [xml]);

	return <Container ref={containerRef} data-testid="bpmn-viewer" />;
}

export {BpmnViewer};
