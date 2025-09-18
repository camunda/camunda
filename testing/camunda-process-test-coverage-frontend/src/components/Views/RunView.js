import React from 'react';
import {toPercentStr} from '../../utils/helpers';

function RunView({ node, onSelect }) {
    return (
        <div>
            <h4>Run: {node.name}</h4>

            <h5 className="mt-4">Covered Processes</h5>
            {node.coverages && node.coverages.length > 0 ? (
                <div className="list-group mb-4">
                    {node.coverages.map((process, idx) => (
                        <div key={idx}
                             className="list-group-item d-flex justify-content-between align-items-center"
                             onClick={() => {
                               onSelect("process", process);
                             }}>
                            <span>{process.processDefinitionId}</span>
                            <span className="badge bg-primary">{toPercentStr(process.coverage)}</span>
                        </div>
                    ))}
                </div>
            ) : (
                <p>No process coverage information available</p>
            )}

            <h5 className="mt-4">Run Statistics</h5>
            <p>Total processes covered: {node.coverages?.length || 0}</p>
            {node.coverages && node.coverages.length > 0 ? (
                <p>
                    Average coverage: {toPercentStr(
                        node.coverages.reduce((sum, p) => sum + p.coverage, 0) / node.coverages.length
                    )}
                </p>
            ) : null}
        </div>
    );
}

export default RunView;
