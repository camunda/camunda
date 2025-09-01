import React, {useState} from 'react';
import ProcessTreeNode from './ProcessTreeNode';
import RunTreeNode from './RunTreeNode';

function SuiteTreeNode({ node, onSelect }) {
    const [expanded, setExpanded] = useState(false);

    return (
        <li>
            <div
                className="tree-node"
                onClick={() => {
                    if (node.runs) {
                        setExpanded(!expanded);
                    }
                    onSelect("suite", node);
                }}
            >
                {expanded ? "▼ " : "▶ "}
                <i className={`bi bi-folder me-2`}></i>
                <span>{node.name}</span>
            </div>
            {expanded && (
                <ul className="list-unstyled ms-3">
                    {node.coverages.map((child, idx) => (
                        <ProcessTreeNode key={idx} node={child} onSelect={onSelect} />
                    ))}
                    {node.runs.map((child, idx) => (
                        <RunTreeNode key={idx} node={child} onSelect={onSelect} />
                    ))}
                </ul>
            )}
        </li>
    );
}

export default SuiteTreeNode;
