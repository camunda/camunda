
import React, {useEffect, useRef} from 'react';
import BpmnJS from 'bpmn-js';
import {toPercentStr} from '../../utils/helpers';

const ProcessView = ({ node, definitions }) => {
    const canvasRef = useRef(null);

    useEffect(() => {
        if (!node || !node.processDefinitionId) return;

        const renderDiagram = async () => {
            if (canvasRef.current) {
                canvasRef.current.innerHTML = '';
            }

            const bpmnViewer = new BpmnJS({
                container: canvasRef.current
            });

            try {
                await bpmnViewer.importXML(definitions[node.processDefinitionId]);
                const canvas = bpmnViewer.get('canvas');
                const elementRegistry = bpmnViewer.get("elementRegistry");
                const graphicsFactory = bpmnViewer.get("graphicsFactory");

                canvas.zoom('fit-viewport');

                if (node.completedElements) {
                    node.completedElements.forEach(elementId => {
                        canvas.addMarker(elementId, 'highlight');
                    });
                }

                if (node.takenSequenceFlows) {
                    node.takenSequenceFlows.forEach(flowId => {
                        // canvas.addMarker(flowId, 'highlight');

                        let sequenceFlow = elementRegistry.get(flowId);
                        let gfx = elementRegistry.getGraphics(sequenceFlow);

                        let color = '#0072CE'; // Bernd’s Hoodie
                        let di = sequenceFlow.businessObject.di;
                        di.set("stroke", color);
                        di.set("fill", color);

                        graphicsFactory.update("connection", sequenceFlow, gfx);
                    });
                }
            } catch (err) {
                console.error('Could not import BPMN diagram', err);
            }
        };

        renderDiagram();

        return () => {
            if (canvasRef.current) {
                canvasRef.current.innerHTML = '';
            }
        };
    }, [node]);

    return (
        <div>
            <h4>Process: {node.processDefinitionId}</h4>
            <div
                ref={canvasRef}
                style={{
                    height: '500px',
                    border: '1px solid #ccc',
                    marginBottom: '20px'
                }}
            />
            <p>Coverage: {toPercentStr(node.coverage)}</p>
        </div>
    );
};

export default ProcessView;
