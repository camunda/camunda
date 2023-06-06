/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {withRouter} from 'react-router-dom';
import equal from 'fast-deep-equal';

import {loadReports, getCollection} from 'services';
import {ColorPicker, CarbonPopover} from 'components';
import {t} from 'translation';

import {Configuration} from './Configuration';
import {TypeaheadMultipleSelection} from './TypeaheadMultipleSelection';

import './CombinedReportPanel.scss';

export default withRouter(
  class CombinedReportPanel extends React.Component {
    constructor(props) {
      super(props);

      this.state = {
        reports: [],
        searchQuery: '',
      };
    }

    async componentDidMount() {
      const acceptedVisualizations = ['table', 'bar', 'line', 'number'];

      const collection = getCollection(this.props.location.pathname) || null;

      const reports = (await loadReports(collection)).filter(
        (report) =>
          !report.combined &&
          report.reportType === 'process' &&
          acceptedVisualizations.includes(report.data.visualization) &&
          report.data.distributedBy.type === 'none' &&
          report.data.view.properties.length === 1 &&
          report.data.configuration.aggregationTypes.length === 1 &&
          report.data.view.properties[0] !== 'rawData'
      );

      this.setState({reports});
    }

    update = (selectedReport, checked) => {
      const selectedReports = this.getSelectedReports();

      let newSelected = [];
      if (checked) {
        newSelected = [...selectedReports, selectedReport];
      } else {
        newSelected = selectedReports.filter((report) => report.id !== selectedReport.id);
      }

      const change = {
        visualization: {$set: selectedReport.data.visualization},
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

    getSelectedReports = () => {
      return this.props.report.data.reports
        .map(({id}) => this.state.reports.find((report) => report.id === id))
        .filter((v) => v);
    };

    getUpdatedColors = (newSelected) => {
      const prevOrderReports = this.getSelectedReports();
      const {reports} = this.props.report.data;
      let colorsHash = {};
      if (reports.length) {
        colorsHash = prevOrderReports.reduce((colors, report, i) => {
          return {...colors, [report.id]: reports[i].color};
        }, {});
      }

      const colors = ColorPicker.getGeneratedColors(newSelected.length).filter(
        (color) => !Object.values(colorsHash).includes(color)
      );

      return newSelected.map((report) => colorsHash[report.id] || colors.pop());
    };

    updateReportsOrder = (newSelected) => {
      let updatedColors = [];
      if (newSelected[0].data.visualization !== 'table') {
        updatedColors = this.getUpdatedColors(newSelected);
      }

      const change = {
        reports: {
          $set: newSelected.map((report, i) => ({id: report.id, color: updatedColors[i]})),
        },
      };

      this.props.updateReport(change);
    };

    isCompatible = (report) => {
      const referenceReport = this.getSelectedReports()[0];
      if (!referenceReport) {
        return true;
      }
      const referenceReportData = referenceReport.data;
      return (
        !referenceReportData ||
        (this.checkSameGroupBy(referenceReportData, report.data) &&
          this.checkSameView(referenceReportData, report.data) &&
          this.checkSameConfiguration(referenceReportData, report.data) &&
          report.data.visualization === referenceReportData.visualization)
      );
    };

    checkSameView = (referenceReport, data) => {
      if (data.view.entity === 'variable' && referenceReport.view.entity === 'variable') {
        return true;
      }

      const sameEntity =
        data.view.entity === referenceReport.view.entity ||
        (data.view.entity === 'flowNode' && referenceReport.view.entity === 'userTask') ||
        (data.view.entity === 'userTask' && referenceReport.view.entity === 'flowNode');

      const identicalProperty = data.view.properties[0] === referenceReport.view.properties[0];
      const bothDurationReports =
        data.view.properties[0].toLowerCase?.().includes('duration') &&
        referenceReport.view.properties[0].toLowerCase?.().includes('duration');
      const bothVariableReports =
        data.view.entity === 'variable' && referenceReport.view.entity === 'variable';

      const sameProperty = identicalProperty || bothDurationReports || bothVariableReports;

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
        convertGroupByType(data.groupBy.type) ===
          convertGroupByType(referenceReport.groupBy.type) && isSameValue
      );
    };

    checkSameConfiguration = ({groupBy, configuration}, data) => {
      if (groupBy?.type.toLowerCase().includes('variable')) {
        if (groupBy.value?.type === 'Date') {
          return (
            configuration?.groupByDateVariableUnit === data.configuration.groupByDateVariableUnit
          );
        } else if (
          ['Integer', 'Double', 'Short', 'Long'].includes(groupBy.value?.type) &&
          configuration?.customBucket.active
        ) {
          return equal(configuration.customBucket, data.configuration.customBucket);
        }
      }
      return true;
    };

    updateColor = (idx) => (color) => {
      const {
        report: {data},
        updateReport,
      } = this.props;

      const newReports = [...data.reports];
      newReports[idx] = {id: newReports[idx].id, color};
      updateReport({
        reports: {
          $set: newReports,
        },
      });
    };

    search = (searchQuery, name) =>
      searchQuery ? name.toLowerCase().includes(searchQuery.toLowerCase()) : true;

    render() {
      const {reports, searchQuery} = this.state;
      const {report: combinedReport, updateReport, loading} = this.props;
      const reportsData = combinedReport.data.reports;
      let selectedReports = [];

      const combinableReportList = reports.filter(
        (report) =>
          this.search(searchQuery, report.name) &&
          this.isCompatible(report) &&
          !(reportsData || []).some(({id}) => id === report.id)
      );

      let configurationType;
      if (reportsData.length) {
        selectedReports = this.getSelectedReports();
        configurationType = combinedReport.data.visualization;
        // combined number reports have bar chart visualization
        if (configurationType === 'number') {
          configurationType = 'bar';
        }
      }

      const instanceCount = combinedReport.result?.instanceCount;

      return (
        <div className="CombinedReportPanel">
          <Configuration
            type={configurationType}
            report={combinedReport}
            onChange={updateReport}
            disabled={loading || !combinedReport.result}
          />
          <TypeaheadMultipleSelection
            availableValues={combinableReportList}
            selectedValues={selectedReports}
            setFilter={(searchQuery) => this.setState({searchQuery})}
            toggleValue={this.update}
            labels={{
              available: t('report.combined.multiSelect.available'),
              selected: (
                <>
                  {t('report.combined.multiSelect.selected')}{' '}
                  {instanceCount > 0 && (
                    <span className="instanceCount">
                      {instanceCount}{' '}
                      {instanceCount.length === 1
                        ? t('common.instance.label')
                        : t('common.instance.label-plural')}
                    </span>
                  )}
                </>
              ),
              search: t('report.combined.multiSelect.search'),
              empty: t('report.combined.multiSelect.empty'),
            }}
            loading={false}
            onOrderChange={this.updateReportsOrder}
            format={(v) => v.name}
            customItemSettings={(val, idx) => {
              if (!configurationType || configurationType === 'table') {
                return;
              }
              const selectedColor = reportsData[idx].color;
              if (!selectedColor) {
                return;
              }
              return (
                <CarbonPopover
                  title={
                    <span
                      className="colorBox"
                      style={{
                        background: selectedColor,
                      }}
                    />
                  }
                >
                  <ColorPicker selectedColor={selectedColor} onChange={this.updateColor(idx)} />
                </CarbonPopover>
              );
            }}
          />
        </div>
      );
    }
  }
);

function convertGroupByType(type) {
  if (['startDate', 'endDate', 'runningDate'].includes(type)) {
    return 'date';
  }
  if (type === 'userTasks') {
    return 'flowNodes';
  }
  return type;
}
