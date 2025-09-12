import React from 'react';
import ProcessTreeNode from './ProcessTreeNode';
import SuiteTreeNode from './SuiteTreeNode';

function Tree({ data, onSelect }) {
    return (
        <ul className="tree list-unstyled">
            {data.suites.map((node, idx) => (
                <SuiteTreeNode key={idx} node={node} onSelect={onSelect} />
            ))}
            {data.coverages.map((node, idx) => (
                <ProcessTreeNode key={idx} node={node} onSelect={onSelect} />
            ))}
        </ul>
    );
}

export default Tree;