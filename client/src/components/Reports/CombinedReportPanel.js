import React from 'react';
import {loadEntity} from 'services';
import {TypeaheadMultipleSelection, Popover, ColorPicker} from 'components';
import {Configuration} from './Configuration';

import './CombinedReportPanel.scss';

export default class CombinedReportPanel extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      reports: [],
      searchQuery: ''
    };
  }

  async componentDidMount() {
    const acceptedVisualizations = ['table', 'bar', 'line', 'number'];

    const reports = (await loadEntity('report')).filter(
      report =>
        !report.combined &&
        acceptedVisualizations.includes(report.data.visualization) &&
        report.data.view.operation !== 'rawData'
    );

    this.setState({reports});
  }

  update = (selectedReport, checked) => {
    const selectedReports = this.getReportResult();

    let newSelected = [];
    if (checked) {
      newSelected = [...selectedReports, selectedReport];
    } else {
      newSelected = selectedReports.filter(report => report.id !== selectedReport.id);
    }

    const change = {
      reportIds: {$set: newSelected.map(report => report.id)},
      visualization: {$set: selectedReport.data.visualization}
    };

    if (selectedReport.data.visualization !== 'table') {
      change.configuration = {
        reportColors: {$set: this.getUpdatedColors(selectedReports, newSelected)}
      };
    }

    this.props.updateReport(change, true);
  };

  getReportResult = () => {
    if (!this.props.report.result) {
      return [];
    }

    return this.props.report.data.reportIds.map(id => this.props.report.result[id]);
  };

  getUpdatedColors = (prevOrderReports, newSelected) => {
    const {configuration} = this.props.report.data;
    const selectedReports = newSelected || this.getReportResult();
    let colorsHash = {};
    if (configuration.reportColors)
      colorsHash = prevOrderReports.reduce((colors, report, i) => {
        return {...colors, [report.id]: configuration.reportColors[i]};
      }, {});

    const colors = ColorPicker.getColors(selectedReports.length).filter(
      color => !Object.values(colorsHash).includes(color)
    );

    return selectedReports.map((report, i) => colorsHash[report.id] || colors.pop());
  };

  updateReportsOrder = selectedReports => {
    const prevOrderReports = selectedReports;
    const change = {
      reportIds: {$set: selectedReports.map(report => report.id)}
    };

    if (selectedReports[0].data.visualization !== 'table') {
      change.configuration = {reportColors: {$set: this.getUpdatedColors(prevOrderReports)}};
    }

    this.props.updateReport(change);
  };

  isCompatible = report => {
    const referenceReport = this.getReportResult()[0];
    if (!referenceReport) return true;
    const referenceReportData = referenceReport.data;
    return (
      !referenceReportData ||
      (this.checkSameGroupBy(referenceReportData, report.data) &&
        report.data.visualization === referenceReportData.visualization &&
        report.data.view.property === referenceReportData.view.property &&
        report.data.view.entity === referenceReportData.view.entity)
    );
  };

  checkSameGroupBy = (referenceReport, data) => {
    let isSameValue = true;
    if (referenceReport.groupBy.value && data.groupBy.value) {
      isSameValue =
        referenceReport.groupBy.value.unit === data.groupBy.value.unit &&
        referenceReport.groupBy.value.name === data.groupBy.value.name;
    }
    return data.groupBy.type === referenceReport.groupBy.type && isSameValue;
  };

  updateColor = idx => color => {
    const {
      report: {data},
      updateReport
    } = this.props;
    const newColorConfiguration = [...data.configuration.reportColors];
    newColorConfiguration[idx] = color;
    updateReport({
      configuration: {
        reportColors: {$set: newColorConfiguration}
      }
    });
  };

  search = (searchQuery, name) =>
    searchQuery ? name.toLowerCase().includes(searchQuery.toLowerCase()) : true;

  render() {
    const {reports, searchQuery} = this.state;
    const {report: combinedReport, updateReport} = this.props;
    const reportIds = combinedReport.data.reportIds;
    let selectedReports = [];

    const combinableReportList = reports.filter(
      report =>
        this.search(searchQuery, report.name) &&
        this.isCompatible(report) &&
        !(reportIds || []).includes(report.id)
    );

    let configurationType;
    if (reportIds.length) {
      selectedReports = this.getReportResult();
      configurationType = combinedReport.data.visualization;
      // combined number reports have bar chart visualization
      if (configurationType === 'number') configurationType = 'bar';
    }

    return (
      <div className="CombinedReportPanel">
        <Configuration type={configurationType} report={combinedReport} onChange={updateReport} />
        <TypeaheadMultipleSelection
          availableValues={combinableReportList}
          selectedValues={selectedReports}
          setFilter={evt => this.setState({searchQuery: evt.target.value})}
          toggleValue={this.update}
          label="reports"
          loading={false}
          onOrderChange={this.updateReportsOrder}
          format={v => v.name}
          customItemSettings={(val, idx) => {
            if (!configurationType || configurationType === 'table') return;
            const selectedColor = combinedReport.data.configuration.reportColors[idx];
            if (!selectedColor) return;
            return (
              <Popover
                title={
                  <span
                    className="colorBox"
                    style={{
                      background: selectedColor
                    }}
                  />
                }
              >
                <ColorPicker selectedColor={selectedColor} onChange={this.updateColor(idx)} />
              </Popover>
            );
          }}
        />
      </div>
    );
  }
}
