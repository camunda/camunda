import React from 'react';
import {toPercentStr} from '../../utils/helpers';

function ProcessTreeNode({ node, onSelect }) {
    return (
        <li>
            <div
                className="tree-node"
                onClick={() => {
                    onSelect("process", node);
                }}
            >
                <i className={`bi bi-diagram-3-fill me-2`}></i>
                <span>{node.processDefinitionId}</span>
                <span className="badge bg-primary ms-2">
                    {toPercentStr(node.coverage)}
                </span>
            </div>
        </li>
    );
}

export default ProcessTreeNode;
