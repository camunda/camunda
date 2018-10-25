import React from 'react';
import {loadEntity} from 'services';
import {TypeaheadMultipleSelection} from 'components';

import './CombinedSelectionPanel.scss';

export default class CombinedSelectionPanel extends React.Component {
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
        report.reportType === 'single' &&
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
          reportIds: this.state.selectedReports.map(report => report.id)
        };
        this.props.updateReport(updates);
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

  search = (searchQuery, name) =>
    searchQuery ? name.toLowerCase().includes(searchQuery.toLowerCase()) : true;

  render() {
    const {reports, selectedReports, searchQuery} = this.state;
    const selectedReportsList = selectedReports;
    const combinableReportList = reports.filter(
      report => this.search(searchQuery, report.name) && this.isCompatible(report)
    );

    return (
      <div className="PanelSelection">
        <TypeaheadMultipleSelection
          availableValues={combinableReportList}
          selectedValues={selectedReportsList}
          setFilter={evt => this.setState({searchQuery: evt.target.value})}
          toggleValue={this.update}
          label="reports"
          loading={false}
          format={v => v.name}
        />
      </div>
    );
  }
}
