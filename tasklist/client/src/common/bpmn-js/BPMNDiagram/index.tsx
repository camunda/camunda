/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useRef, useEffect, useLayoutEffect, useState} from 'react';
import {observer} from 'mobx-react-lite';
import {BpmnJS} from 'common/bpmn-js/BpmnJS';
import {DiagramControls} from './DiagramControls';
import styles from './styles.module.scss';

type Props = {
  xml: string;
  highlightActivity?: string[];
  children?: React.ReactNode;
};

function useBpmnJS() {
  const viewerRef = useRef<BpmnJS | null>(null);

  function getViewer() {
    if (viewerRef.current === null) {
      viewerRef.current = new BpmnJS();
    }
    return viewerRef.current;
  }

  return getViewer();
}

const BPMNDiagram: React.FC<Props> = observer(
  ({xml, highlightActivity, children}) => {
    const diagramCanvasRef = useRef<HTMLDivElement | null>(null);
    const [isDiagramRendered, setIsDiagramRendered] = useState(false);
    const viewer = useBpmnJS();

    useLayoutEffect(() => {
      async function renderDiagram() {
        if (diagramCanvasRef.current) {
          setIsDiagramRendered(false);
          await viewer.render({
            container: diagramCanvasRef.current,
            xml,
          });
          if (highlightActivity !== undefined) {
            highlightActivity.forEach((id) => {
              viewer.addMarker(id, 'tl-highlighted-activity');
            });
          }
          setIsDiagramRendered(true);
        }
      }

      renderDiagram();
    }, [xml, viewer, highlightActivity]);

    useEffect(() => {
      return () => {
        viewer.reset();
      };
    }, [viewer]);

    return (
      <div className={styles.container} data-testid="diagram">
        <div className={styles.canvas} ref={diagramCanvasRef} />
        {isDiagramRendered && (
          <>
            <DiagramControls
              handleZoomIn={viewer.zoomIn}
              handleZoomOut={viewer.zoomOut}
              handleZoomReset={viewer.zoomReset}
            />
            {children}
          </>
        )}
      </div>
    );
  },
);

export {BPMNDiagram};
