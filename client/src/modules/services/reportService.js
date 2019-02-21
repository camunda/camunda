export function isDurationReport(report) {
  // waiting for optional chaining... https://github.com/tc39/proposal-optional-chaining
  return (
    report &&
    report.data &&
    report.data.view &&
    report.data.view.property &&
    report.data.view.property.toLowerCase().includes('duration')
  );
}
