export function toPercentStr(value) {
    return (value * 100).toFixed(1) + "%";
}

export function calculateAverageCoverage(processes) {
    if (processes.length === 0) return 0;
    const totalCoverage = processes.reduce((sum, process) => sum + process.coverage, 0);
    return totalCoverage / processes.length;
}

export function getUniqueProcesses(suites) {
    const uniqueProcesses = new Map();
    suites.forEach(suite => {
        if (suite.coverages && suite.coverages.length > 0) {
            suite.coverages.forEach(process => {
                if (!uniqueProcesses.has(process.processDefinitionId) ||
                    uniqueProcesses.get(process.processDefinitionId).coverage < process.coverage) {
                    uniqueProcesses.set(process.processDefinitionId, process);
                }
            });
        }
    });
    return Array.from(uniqueProcesses.values());
}