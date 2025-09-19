import React from 'react';
import ProcessTreeNode from './ProcessTreeNode';
import SuiteTreeNode from './SuiteTreeNode';

function Tree({ data, onSelect }) {
    return (
        <ul className="tree list-unstyled">
          <li className="border-bottom border-2">
            <div
                className="tree-node"
                onClick={() => {
                  onSelect("dashboard", null);
                }}
            >
              <i className={`bi bi-house-fill me-2`}></i>
              <span>Dashboard</span>
            </div>
          </li>
          {data.coverages.map((node, idx) => (
              <ProcessTreeNode key={idx} node={node} onSelect={onSelect}/>
              ))}
          {data.suites.map((node, idx) => (
              <SuiteTreeNode key={idx} node={node} onSelect={onSelect}/>
          ))}
        </ul>
    );
}

export default Tree;
