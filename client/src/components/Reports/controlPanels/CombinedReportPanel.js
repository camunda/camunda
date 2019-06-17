/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {loadEntities} from 'services';
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

    const reports = (await loadEntities('report', 'lastModified')).filter(
      report =>
        !report.combined &&
        report.reportType === 'process' &&
        acceptedVisualizations.includes(report.data.visualization) &&
        report.data.view.property !== 'rawData'
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
      visualization: {$set: selectedReport.data.visualization}
    };

    let updatedColors = [];
    if (selectedReport.data.visualization !== 'table') {
      updatedColors = this.getUpdatedColors(newSelected);
      change.reports = {$set: newSelected.map(({id}, i) => ({id, color: updatedColors[i]}))};
    } else {
      change.reports = {$set: newSelected.map(({id}) => ({id}))};
    }

    this.props.updateReport(change, true);
  };

  getReportResult = () => {
    if (!this.props.report.result) {
      return [];
    }

    // the new report might be in the reports array,
    // but the report has not been evaluated yet,
    // so that the report is not in the results structure yet.
    // we filter all entries which are not truthy to get rid of them
    return this.props.report.data.reports
      .map(({id}) => this.props.report.result.data[id])
      .filter(v => v);
  };

  getUpdatedColors = newSelected => {
    const prevOrderReports = this.getReportResult();
    const {reports} = this.props.report.data;
    let colorsHash = {};
    if (reports.length) {
      colorsHash = prevOrderReports.reduce((colors, report, i) => {
        return {...colors, [report.id]: reports[i].color};
      }, {});
    }

    const colors = ColorPicker.getColors(newSelected.length).filter(
      color => !Object.values(colorsHash).includes(color)
    );

    return newSelected.map(report => colorsHash[report.id] || colors.pop());
  };

  updateReportsOrder = newSelected => {
    let updatedColors = [];
    if (newSelected[0].data.visualization !== 'table') {
      updatedColors = this.getUpdatedColors(newSelected);
    }

    const change = {
      reports: {
        $set: newSelected.map((report, i) => ({id: report.id, color: updatedColors[i]}))
      }
    };

    this.props.updateReport(change);
  };

  isCompatible = report => {
    const referenceReport = this.getReportResult()[0];
    if (!referenceReport) {
      return true;
    }
    const referenceReportData = referenceReport.data;
    return (
      !referenceReportData ||
      (this.checkSameGroupBy(referenceReportData, report.data) &&
        this.checkSameView(referenceReportData, report.data) &&
        report.data.visualization === referenceReportData.visualization)
    );
  };

  checkSameView = (referenceReport, data) => {
    const sameEntity =
      data.view.entity === referenceReport.view.entity ||
      (data.view.entity === 'flowNode' && referenceReport.view.entity === 'userTask') ||
      (data.view.entity === 'userTask' && referenceReport.view.entity === 'flowNode');

    const sameProperty =
      data.view.property === referenceReport.view.property ||
      (data.view.property.toLowerCase().includes('duration') &&
        referenceReport.view.property.toLowerCase().includes('duration'));

    return sameEntity && sameProperty;
  };

  checkSameGroupBy = (referenceReport, data) => {
    let isSameValue = true;
    if (referenceReport.groupBy.value && data.groupBy.value) {
      isSameValue =
        referenceReport.groupBy.value.unit === data.groupBy.value.unit &&
        referenceReport.groupBy.value.name === data.groupBy.value.name;
    }
    return (
      convertGroupByType(data.groupBy.type) === convertGroupByType(referenceReport.groupBy.type) &&
      isSameValue
    );
  };

  updateColor = idx => color => {
    const {
      report: {data},
      updateReport
    } = this.props;

    const newReports = [...data.reports];
    newReports[idx] = {id: newReports[idx].id, color};
    updateReport({
      reports: {
        $set: newReports
      }
    });
  };

  search = (searchQuery, name) =>
    searchQuery ? name.toLowerCase().includes(searchQuery.toLowerCase()) : true;

  render() {
    const {reports, searchQuery} = this.state;
    const {report: combinedReport, updateReport} = this.props;
    const reportsData = combinedReport.data.reports;
    let selectedReports = [];

    const combinableReportList = reports.filter(
      report =>
        this.search(searchQuery, report.name) &&
        this.isCompatible(report) &&
        !(reportsData || []).some(({id}) => id === report.id)
    );

    let configurationType;
    if (reportsData.length) {
      selectedReports = this.getReportResult();
      configurationType = combinedReport.data.visualization;
      // combined number reports have bar chart visualization
      if (configurationType === 'number') {
        configurationType = 'bar';
      }
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
            if (!configurationType || configurationType === 'table') {
              return;
            }
            const selectedColor = reportsData[idx].color;
            if (!selectedColor) {
              return;
            }
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

function convertGroupByType(type) {
  if (type === 'startDate' || type === 'endDate') {
    return 'date';
  }
  return type;
}
