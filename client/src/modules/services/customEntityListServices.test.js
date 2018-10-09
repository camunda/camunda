import {getCustomReportInfo, getCustomDashboardInfo} from './customEntityListServices';

jest.mock('services', () => ({
  extractProcessDefinitionName: () => 'exampleProcessDefName'
}));

describe('getReportCountLabel', () => {
  it('should return empty label if there are no reports', () => {
    const label = getCustomDashboardInfo({reports: []});
    expect(label).toBe('0 Reports');
  });

  it('should return correct single report label', () => {
    const label = getCustomDashboardInfo({reports: [{}]});
    expect(label).toBe('1 Report');
  });

  it('should return correct multiple report label', () => {
    const label = getCustomDashboardInfo({reports: [{}, {}]});
    expect(label).toBe('2 Reports');
  });
});

describe('getCustomReportInfo', () => {
  it('should return empty label if data is null or empty', () => {
    const label = getCustomReportInfo({data: null});
    expect(label).toBe('');
  });

  it('should return the number of report if the report is combined', () => {
    const label = getCustomReportInfo({reportType: 'combined', data: {reportIds: ['1', '2']}});
    expect(label).toBe('2 reports');
  });

  it('should return the process definition name if the report is single', () => {
    const label = getCustomReportInfo({data: {configuration: {xml: ' '}}});
    expect(label).toBe('exampleProcessDefName');
  });
});
