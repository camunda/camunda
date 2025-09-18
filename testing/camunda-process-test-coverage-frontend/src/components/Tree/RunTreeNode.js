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
                <i className={expanded ? `bi me-2 bi-caret-down-fill` : `bi me-2 bi-caret-right-fill`}></i>
                <i className={`bi bi-file-earmark-fill me-2`}></i>
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
