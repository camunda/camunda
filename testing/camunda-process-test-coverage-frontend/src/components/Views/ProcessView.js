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

                canvas.zoom('fit-viewport');

                if (node.completedElements) {
                    node.completedElements.forEach(elementId => {
                        canvas.addMarker(elementId, 'highlight');
                    });
                }

                if (node.takenSequenceFlows) {
                    node.takenSequenceFlows.forEach(flowId => {
                        canvas.addMarker(flowId, 'highlight');
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

            <h5 className="mt-4">
                Completed Elements: {node.completedElements?.length || 0}
            </h5>
            {node.completedElements && node.completedElements.length > 0 ? (
                <ul className="list-group mb-3">
                    {node.completedElements.map((elementId, idx) => (
                        <li key={idx} className="list-group-item">
                            {elementId}
                        </li>
                    ))}
                </ul>
            ) : (
                <p>No completed elements</p>
            )}

            <h5 className="mt-4">
                Taken Sequence Flows: {node.takenSequenceFlows?.length || 0}
            </h5>
            {node.takenSequenceFlows && node.takenSequenceFlows.length > 0 ? (
                <ul className="list-group">
                    {node.takenSequenceFlows.map((flowId, idx) => (
                        <li key={idx} className="list-group-item">
                            {flowId}
                        </li>
                    ))}
                </ul>
            ) : (
                <p>No taken sequence flows</p>
            )}
        </div>
    );
};

export default ProcessView;
