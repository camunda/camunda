/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {Component} from 'react';
import update from 'immutability-helper';
import {withErrorHandling} from 'HOC';
import moment from 'moment';
import {Redirect} from 'react-router-dom';
import {
  ReportRenderer,
  LoadingIndicator,
  Message,
  ConfirmationModal,
  EntityNameForm,
  CollectionsDropdown
} from 'components';

import {evaluateReport, saveReport} from './service';

import {
  loadDefinitions,
  incompatibleFilters,
  loadProcessDefinitionXml,
  toggleEntityCollection
} from 'services';
import {addNotification} from 'notifications';
import ReportControlPanel from './controlPanels/ReportControlPanel';
import DecisionControlPanel from './controlPanels/DecisionControlPanel';
import CombinedReportPanel from './controlPanels/CombinedReportPanel';

export default withErrorHandling(
  class ReportEdit extends Component {
    state = {
      loadingReportData: false,
      redirect: '',
      confirmModalVisible: false,
      conflict: null,
      originalData: this.props.report,
      report: this.props.report,
      saveLoading: false
    };

    getTheOnlyDefinition = async () => {
      const availableDefinitions = await loadDefinitions('process');
      if (availableDefinitions.length === 1) {
        const theOnlyKey = availableDefinitions[0].key;
        const latestVersion = availableDefinitions[0].versions[0].version;
        return {theOnlyKey, latestVersion};
      }

      return {theOnlyKey: null, latestVersion: null};
    };

    loadTheOnlyDefinition = async () => {
      const {theOnlyKey, latestVersion} = await this.getTheOnlyDefinition();

      const data = {
        processDefinitionKey: theOnlyKey || '',
        processDefinitionVersion: latestVersion || ''
      };

      await this.loadXmlToConfiguration(data);

      return data;
    };

    loadXmlToConfiguration = async data => {
      if (data.processDefinitionKey && data.processDefinitionVersion) {
        const xml = await loadProcessDefinitionXml(
          data.processDefinitionKey,
          data.processDefinitionVersion
        );
        data.configuration = {...data.configuration, xml};
      }
    };

    save = async (evt, updatedName) => {
      const {id, data, reportType, combined} = this.state.report;
      const name = updatedName || this.state.report.name;
      this.setState({saveLoading: true});
      await this.props.mightFail(
        saveReport(id, {name, data, reportType, combined}, this.state.conflict !== null),
        () => {
          addNotification({
            text: `Report "${updatedName}" saved.`,
            type: 'success',
            duration: 2000
          });
          this.setState({
            confirmModalVisible: false,
            report: update(this.state.report, {name: {$set: name}}),
            originalData: this.state.report,
            redirect: `/report/${id}`,
            conflict: null,
            saveLoading: false
          });
          this.props.updateOverview(this.state.report);
        },
        async error => {
          if (error.statusText === 'Conflict') {
            const conflictData = await error.json();
            this.setState({
              report: update(this.state.report, {name: {$set: name}}),
              confirmModalVisible: true,
              saveLoading: false,
              conflict: {
                type: 'Save',
                items: conflictData.conflictedItems
              }
            });
          } else {
            addNotification({text: `Report "${updatedName}" could not be saved.`, type: 'error'});
          }
        }
      );
    };

    cancel = async () => {
      this.setState({
        report: this.state.originalData
      });
    };

    updateReport = async (change, needsReevaluation) => {
      const newReport = update(this.state.report.data, change);

      if (needsReevaluation) {
        const query = {
          ...this.state.report,
          data: newReport
        };

        this.setState({
          loadingReportData: true
        });
        this.setState({
          report: (await evaluateReport(query)) || query,
          loadingReportData: false
        });
      } else {
        this.setState(({report}) => ({
          report: update(report, {data: change})
        }));
      }
    };

    maxRawDataEntriesExceeded = () => {
      if (!this.state.report) {
        return false;
      }

      const {data, result} = this.state.report;
      return !!(
        result &&
        result.data.length &&
        data &&
        data.visualization === 'table' &&
        data.view &&
        (data.view.operation === 'rawData' || data.view.property === 'rawData') &&
        (result.processInstanceCount > result.data.length ||
          result.decisionInstanceCount > result.data.length)
      );
    };

    closeConfirmModal = () => {
      this.setState({
        confirmModalVisible: false,
        conflict: null
      });
    };

    render() {
      const {
        report,
        loadingReportData,
        confirmModalVisible,
        conflict,
        redirect,
        saveLoading
      } = this.state;
      const {id, name, lastModifier, lastModified, data, combined, reportType} = report;
      const {collections, reportCollections, openEditCollectionModal, loadCollections} = this.props;

      if (redirect) {
        return <Redirect to={redirect} />;
      }

      return (
        <>
          <ConfirmationModal
            open={confirmModalVisible}
            onClose={this.closeConfirmModal}
            onConfirm={this.save}
            conflict={conflict}
            entityName={name}
            loading={saveLoading}
          />
          <div className="Report">
            <div className="Report__header">
              <EntityNameForm
                id={id}
                initialName={name}
                entity="Report"
                autofocus={this.props.isNew}
                onSave={this.save}
                onCancel={this.cancel}
                disabledButtons={saveLoading}
              />
              <div className="subHead">
                <div className="metadata">
                  Last modified {moment(lastModified).format('lll')} by {lastModifier}
                </div>
                <CollectionsDropdown
                  entity={report}
                  collections={collections}
                  toggleEntityCollection={toggleEntityCollection(loadCollections)}
                  entityCollections={reportCollections}
                  setCollectionToUpdate={openEditCollectionModal}
                />
              </div>
            </div>

            {!combined && reportType === 'process' && (
              <ReportControlPanel report={report} updateReport={this.updateReport} />
            )}

            {!combined && reportType === 'decision' && (
              <DecisionControlPanel report={report} updateReport={this.updateReport} />
            )}

            {this.maxRawDataEntriesExceeded() && (
              <Message type="warning">
                The raw data table below only shows {report.result.data.length} instances out of a
                total of {report.result.processInstanceCount || report.result.decisionInstanceCount}
              </Message>
            )}

            {data && data.filter && incompatibleFilters(data.filter) && (
              <Message type="warning">
                No data is shown since the combination of filters is incompatible with each other
              </Message>
            )}

            <div className="Report__view">
              <div className="Report__content">
                {loadingReportData ? (
                  <LoadingIndicator />
                ) : (
                  <ReportRenderer report={report} updateReport={this.updateReport} />
                )}
              </div>
              {combined && <CombinedReportPanel report={report} updateReport={this.updateReport} />}
            </div>
          </div>
        </>
      );
    }
  }
);
