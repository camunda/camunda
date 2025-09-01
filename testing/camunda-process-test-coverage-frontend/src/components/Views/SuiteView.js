import React from 'react';
import {toPercentStr} from '../../utils/helpers';

function SuiteView({ node }) {
    return (
        <div>
            <h4>Suite: {node.name}</h4>
            <h5 className="mt-4">Covered Processes</h5>
            {node.coverages && node.coverages.length > 0 ? (
                <div className="list-group mb-4">
                    {node.coverages.map((process, idx) => (
                        <div key={idx} className="list-group-item d-flex justify-content-between align-items-center">
                            <span>{process.processDefinitionId}</span>
                            <div className="ms-auto d-flex align-items-center">
                                <div className="progress me-2" style={{ width: '150px', height: '20px' }}>
                                    <div
                                        className="progress-bar bg-success"
                                        role="progressbar"
                                        style={{ width: `${process.coverage * 100}%` }}
                                        aria-valuenow={process.coverage * 100}
                                        aria-valuemin="0"
                                        aria-valuemax="100"
                                    />
                                </div>
                                <span className="badge bg-secondary">{toPercentStr(process.coverage)}</span>
                            </div>
                        </div>
                    ))}
                </div>
            ) : (
                <p>No process coverage information available</p>
            )}
            <h5 className="mt-4">Test Runs</h5>
            {node.runs && node.runs.length > 0 ? (
                <ul className="list-group">
                    {node.runs.map((run, idx) => (
                        <li key={idx} className="list-group-item d-flex justify-content-between align-items-center">
                            <span>{run.name}</span>
                            {run.coverages && run.coverages.length > 0 ? (
                                <span className="badge bg-info rounded-pill">
                                    {run.coverages.length} process{run.coverages.length !== 1 ? 'es' : ''}
                                </span>
                            ) : null}
                        </li>
                    ))}
                </ul>
            ) : (
                <p>No test runs available</p>
            )}
        </div>
    );
}

export default SuiteView;
