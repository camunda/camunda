/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useLayoutEffect, useRef, useState} from 'react';
import {BpmnJS} from './BpmnJS';
import {DiagramControls} from './DiagramControls';
import styles from './BPMNDiagram.module.scss';

type Props = {
	xml: string;
	highlightActivity?: string;
};

const BPMNDiagram: React.FC<Props> = ({xml, highlightActivity}) => {
	const diagramCanvasRef = useRef<HTMLDivElement | null>(null);
	const [viewer] = useState(() => new BpmnJS());
	const [isDiagramRendered, setIsDiagramRendered] = useState(false);

	useLayoutEffect(() => {
		let isMounted = true;

		async function renderDiagram() {
			if (diagramCanvasRef.current === null) {
				return;
			}

			setIsDiagramRendered(false);
			await viewer.render({container: diagramCanvasRef.current, xml});

			if (highlightActivity !== undefined) {
				viewer.addMarker(highlightActivity, 'tasklist-highlighted-activity');
			}

			if (isMounted) {
				setIsDiagramRendered(true);
			}
		}

		renderDiagram();

		return () => {
			isMounted = false;
		};
	}, [xml, highlightActivity, viewer]);

	useEffect(() => {
		return () => {
			viewer.reset();
		};
	}, [viewer]);

	return (
		<div className={styles.container} data-testid="diagram">
			<div className={styles.canvas} ref={diagramCanvasRef} />
			{isDiagramRendered ? (
				<DiagramControls onZoomReset={viewer.zoomReset} onZoomIn={viewer.zoomIn} onZoomOut={viewer.zoomOut} />
			) : null}
		</div>
	);
};

export {BPMNDiagram};
