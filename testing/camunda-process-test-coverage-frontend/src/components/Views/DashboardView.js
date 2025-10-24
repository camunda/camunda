import React from 'react';
import {toPercentStr} from '../../utils/helpers';

function DashboardView({ data, onSelect }) {
    // Add a safety check for props
    if (!data) {
        return <div className="alert alert-danger">No coverage data available</div>;
    }
    const allSuites = data.suites || [];
    const totalSuites = allSuites.length;

    const uniqueProcesses = new Map();
    allSuites.forEach(suite => {
        if (suite.coverages && suite.coverages.length > 0) {
            suite.coverages.forEach(process => {
                if (!uniqueProcesses.has(process.processDefinitionId) ||
                    uniqueProcesses.get(process.processDefinitionId).coverage < process.coverage) {
                    uniqueProcesses.set(process.processDefinitionId, process);
                }
            });
        }
    });

    const allProcesses = Array.from(uniqueProcesses.values());
    const avgCoverage = allProcesses.length > 0 ?
        allProcesses.reduce((sum, process) => sum + process.coverage, 0) / allProcesses.length : 0;

    return (
        <div>
            <h3>Coverage Dashboard</h3>
            <div className="row mt-4">
                <div className="col-md-4">
                    <div className="card text-center">
                        <div className="card-body">
                            <h5 className="card-title">{totalSuites}</h5>
                            <p className="card-text">Test Suites</p>
                        </div>
                    </div>
                </div>
                <div className="col-md-4">
                    <div className="card text-center">
                        <div className="card-body">
                            <h5 className="card-title">{allProcesses.length}</h5>
                            <p className="card-text">Processes Covered</p>
                        </div>
                    </div>
                </div>
                <div className="col-md-4">
                    <div className="card text-center">
                        <div className="card-body">
                            <h5 className="card-title">{toPercentStr(avgCoverage)}</h5>
                            <p className="card-text">Average Coverage</p>
                        </div>
                    </div>
                </div>
            </div>
            <h4 className="mt-4">Process Coverage Overview</h4>
            {allProcesses.length > 0 ? (
                <div className="list-group">
                    {allProcesses.sort((a, b) => b.coverage - a.coverage)
                        .map((process, idx) => (
                            <div key={idx}
                                 className="list-group-item d-flex justify-content-between align-items-center"
                                 onClick={() => {
                                   onSelect("process", process);
                                 }}>
                                <span>{process.processDefinitionId}</span>
                                <div className="ms-auto d-flex align-items-center">
                                    <div className="progress me-2" style={{ width: "150px", height: "20px" }}>
                                        <div className="progress-bar bg-success" role="progressbar" style={{ width: (process.coverage * 100) + "%" }} aria-valuenow={process.coverage * 100} aria-valuemin="0" aria-valuemax="100"></div>
                                    </div>
                                    <span className="badge bg-primary">{toPercentStr(process.coverage)}</span>
                                </div>
                            </div>
                        ))}
                </div>
            ) : (
                <p>No process coverage information available</p>
            )}
            <h4 className="mt-4">Test Suites</h4>
            <ul className="list-group">
                {allSuites.map((suite, idx) => (
                    <li key={idx}
                        className="list-group-item d-flex justify-content-between align-items-center"
                        onClick={() => {
                          onSelect("suite", suite);
                        }}>
                        <span>{suite.name}</span>
                    </li>
                ))}
            </ul>
        </div>
    );
}

export default DashboardView;
