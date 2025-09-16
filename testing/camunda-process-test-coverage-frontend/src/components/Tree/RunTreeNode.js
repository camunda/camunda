import React, {useState} from 'react';
import ProcessTreeNode from './ProcessTreeNode';

function RunTreeNode({ node, onSelect }) {
    const [expanded, setExpanded] = useState(false);

    return (
        <li>
            <div
                className="tree-node"
                onClick={() => {
                    setExpanded(!expanded);
                    onSelect("run", node);
                }}
            >
                {expanded ? "▼ " : "▶ "}
                <i className={`bi bi-flask me-2`}></i>
                <span>{node.name}</span>
            </div>
            {expanded && (
                <ul className="list-unstyled ms-3">
                    {node.coverages.map((child, idx) => (
                        <ProcessTreeNode key={idx} node={child} onSelect={onSelect} />
                    ))}
                </ul>
            )}
        </li>
    );
}

export default RunTreeNode;
