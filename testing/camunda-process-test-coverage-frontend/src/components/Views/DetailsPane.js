import React from 'react';
import DashboardView from './DashboardView';
import ProcessView from './ProcessView';
import RunView from './RunView';
import SuiteView from './SuiteView';

const DetailsPane = ({ data, node, type, onSelect }) => {
    if (!node) {
        return <DashboardView data={data} onSelect={onSelect} />;
    }
    switch (type) {
        case 'suite':
            return <SuiteView node={node} onSelect={onSelect} />;
        case 'run':
            return <RunView node={node} onSelect={onSelect} />;
        case 'process':
            return <ProcessView node={node} definitions={data.definitions} />;
        default:
            return <div>Unknown node type.</div>;
    }
};

export default DetailsPane;
