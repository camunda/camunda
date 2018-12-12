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
      selectedReports: [],
      referenceReport: null,
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

    const reportIds = this.props.reportResult.data.reportIds;

    if (!reportIds || !reportIds.length) return this.setState({reports});

    const selectedReports = reports.filter(report =>
      this.props.reportResult.data.reportIds.includes(report.id)
    );

    const notSelectedReports = reports.filter(
      report => !this.props.reportResult.data.reportIds.includes(report.id)
    );

    this.setState({
      reports: notSelectedReports,
      selectedReports,
      referenceReport: selectedReports.length ? selectedReports[0].data : null
    });
  }

  update = (selectedReport, checked) => {
    const {selectedReports, reports} = this.state;
    let newReports = [];
    let newSelected = [];
    if (checked) {
      newReports = reports.filter(report => report.id !== selectedReport.id);
      newSelected = [...selectedReports, selectedReport];
    } else {
      newReports = [...reports, selectedReport];
      newSelected = selectedReports.filter(report => report.id !== selectedReport.id);
    }

    this.setState(
      {
        reports: newReports,
        selectedReports: newSelected,
        referenceReport: newSelected.length ? selectedReport.data : null
      },
      () => {
        const updates = {
          reportIds: this.state.selectedReports.map(report => report.id),
          configuration: {
            ...this.props.configuration,
            ...(selectedReport.data.visualization !== 'table'
              ? {color: this.getUpdatedColors(selectedReports)}
              : {})
          }
        };
        this.props.updateReport(updates);
      }
    );
  };

  getUpdatedColors = prevOrderReports => {
    const colorsHash = prevOrderReports.reduce((colors, report, i) => {
      return {...colors, [report.id]: this.props.configuration.color[i]};
    }, {});

    const colors = ColorPicker.getColors(this.state.selectedReports.length).filter(
      color => !Object.values(colorsHash).includes(color)
    );
    colors[0] = ColorPicker.dark.steelBlue;

    return this.state.selectedReports.map((report, i) => colorsHash[report.id] || colors.pop());
  };

  updateReportsOrder = selectedReports => {
    const prevOrderReports = this.state.selectedReports;
    this.setState(
      {
        selectedReports
      },
      () => {
        this.props.updateReport({
          reportIds: this.state.selectedReports.map(report => report.id),
          configuration: {
            ...this.props.configuration,
            ...(selectedReports[0].data.visualization !== 'table'
              ? {color: this.getUpdatedColors(prevOrderReports)}
              : {})
          }
        });
      }
    );
  };

  isCompatible = report => {
    const {referenceReport} = this.state;
    return (
      !referenceReport ||
      (this.checkSameGroupBy(referenceReport, report.data) &&
        report.data.visualization === referenceReport.visualization &&
        report.data.view.property === referenceReport.view.property &&
        report.data.view.entity === referenceReport.view.entity)
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
    const {configuration, updateReport} = this.props;
    const newColorConfiguration = [...configuration.color];
    newColorConfiguration[idx] = color;
    updateReport({
      configuration: {
        ...configuration,
        color: newColorConfiguration
      }
    });
  };

  search = (searchQuery, name) =>
    searchQuery ? name.toLowerCase().includes(searchQuery.toLowerCase()) : true;

  render() {
    const {reports, selectedReports, searchQuery} = this.state;
    const {reportResult, configuration, updateReport} = this.props;

    const combinableReportList = reports.filter(
      report => this.search(searchQuery, report.name) && this.isCompatible(report)
    );

    let configurationType;
    if (reportResult.data.reportIds && reportResult.data.reportIds.length) {
      configurationType = Object.values(reportResult.result)[0].data.visualization;
      // combined number reports have bar chart visualization
      if (configurationType === 'number') configurationType = 'bar';
    }

    return (
      <div className="CombinedReportPanel">
        <Configuration
          type={configurationType}
          report={reportResult}
          configuration={configuration}
          onChange={updateReport}
        />
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
            const selectedColor = configuration.color && configuration.color[idx];
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
