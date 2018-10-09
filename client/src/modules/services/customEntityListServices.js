import {extractProcessDefinitionName} from 'services';

export function getCustomReportInfo(report) {
  if (report.data) {
    // if not empty combined
    if (report.reportType === 'combined' && report.data.reportIds && report.data.reportIds.length) {
      const reportsCount = report.data.reportIds.length;
      return `${reportsCount} report${reportsCount !== 1 ? 's' : ''}`;
    }
    // if normal report
    if (report.data.configuration.xml) {
      return extractProcessDefinitionName(report.data.configuration.xml);
    }
  }
  return '';
}

export function getCustomDashboardInfo(dashboard) {
  if (dashboard.reports.length === 1) return '1 Report';
  return dashboard.reports.length + ' Reports';
}
