/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import classnames from 'classnames';

import {DefinitionSelection, Icon, Button} from 'components';
import {DecisionFilter} from 'filter';
import {
  loadInputVariables,
  loadOutputVariables,
  reportConfig,
  loadDecisionDefinitionXml,
} from 'services';
import {t} from 'translation';
import {withErrorHandling} from 'HOC';
import {showError} from 'notifications';

import ReportSelect from './ReportSelect';

const {decision: decisionConfig} = reportConfig;

export class DecisionControlPanel extends React.Component {
  state = {
    variables: {
      inputVariable: null,
      outputVariable: null,
    },
    scrolled: false,
    showSource: true,
    showSetup: true,
    showFilter: true,
  };

  componentDidMount() {
    this.loadVariables(this.props.report.data);
  }

  loadVariables = ({decisionDefinitionKey, decisionDefinitionVersions, tenantIds}) => {
    if (decisionDefinitionKey && decisionDefinitionVersions && tenantIds) {
      const payload = {decisionDefinitionKey, decisionDefinitionVersions, tenantIds};
      return new Promise((resolve, reject) => {
        this.props.mightFail(
          Promise.all([loadInputVariables(payload), loadOutputVariables(payload)]),
          ([inputVariable, outputVariable]) =>
            this.setState({variables: {inputVariable, outputVariable}}, resolve),
          (error) => reject(showError(error))
        );
      });
    }
  };

  loadXml = ({decisionDefinitionKey, decisionDefinitionVersions, tenantIds}) => {
    if (decisionDefinitionKey && decisionDefinitionVersions?.[0] && tenantIds) {
      return new Promise((resolve, reject) => {
        this.props.mightFail(
          loadDecisionDefinitionXml(
            decisionDefinitionKey,
            decisionDefinitionVersions[0],
            tenantIds[0]
          ),
          resolve,
          (error) => reject(showError(error))
        );
      });
    }

    return null;
  };

  variableExists = (type, varName) =>
    this.state.variables[type].some((variable) => variable.id === varName);

  getNewVariables = (columns) => {
    const types = ['input', 'output'];
    return Object.values(this.state.variables)
      .map((variables, i) => variables.map((variable) => types[i] + ':' + variable.id))
      .flat()
      .filter((col) => !columns.includes(col));
  };

  filterNonExistingVariables = (columns) =>
    columns.filter((col) => {
      if (col.startsWith('input:') || col.startsWith('output:')) {
        const [type, name] = col.split(':');
        return this.variableExists(type + 'Variable', name);
      }

      return true;
    });

  changeDefinition = async ({key, versions, tenantIds, name}) => {
    const {groupBy, configuration} = this.props.report.data;
    const {columnOrder, includedColumns, excludedColumns} = configuration.tableColumns;
    const definitionData = {
      decisionDefinitionKey: key,
      decisionDefinitionVersions: versions,
      tenantIds,
    };

    this.props.setLoading(true);
    const [xml] = await Promise.all([
      this.loadXml(definitionData),
      this.loadVariables(definitionData),
    ]);

    const change = {
      decisionDefinitionKey: {$set: key},
      decisionDefinitionName: {$set: name},
      decisionDefinitionVersions: {$set: versions},
      tenantIds: {$set: tenantIds},
      configuration: {xml: {$set: xml}},
    };

    const variableReport = ['inputVariable', 'outputVariable'].includes(groupBy?.type);

    if (variableReport && !this.variableExists(groupBy.type, groupBy.value.id)) {
      change.groupBy = {$set: null};
      change.visualization = {$set: null};
    }

    if (columnOrder.length) {
      change.configuration.tableColumns = {
        columnOrder: {$set: this.filterNonExistingVariables(columnOrder)},
      };
    }

    if (includedColumns.length) {
      change.configuration.tableColumns = {
        ...change.configuration.tableColumns,
        includedColumns: {
          $set: includedColumns.concat(
            this.getNewVariables(includedColumns.concat(excludedColumns))
          ),
        },
      };
    }

    await this.props.updateReport(change, true);
    this.props.setLoading(false);
  };

  updateReport = (type, newValue) => {
    this.props.updateReport(decisionConfig.update(type, newValue, this.props), true);
  };

  render() {
    const {data, result} = this.props.report;
    const {
      decisionDefinitionKey,
      decisionDefinitionVersions,
      tenantIds,
      filter,
      configuration: {xml},
    } = data;
    const {showSource, showSetup, showFilter, scrolled} = this.state;

    return (
      <div className="DecisionControlPanel ReportControlPanel">
        <section className={classnames('select', 'source', {hidden: !showSource})}>
          <h3 className="sectionTitle">
            <Icon type="data-source" />
            {t('common.dataSource')}
            <Button
              icon
              className="sectionToggle"
              onClick={() => {
                this.setState({showSource: !showSource});
              }}
            >
              <Icon type={showSource ? 'up' : 'down'} />
            </Button>
          </h3>
          <DefinitionSelection
            type="decision"
            definitionKey={decisionDefinitionKey}
            versions={decisionDefinitionVersions}
            tenants={tenantIds}
            xml={xml}
            onChange={this.changeDefinition}
          />
        </section>
        <section className={classnames('reportSetup', {hidden: !showSetup})}>
          <h3 className="sectionTitle">
            <Icon type="report" />
            {t('report.reportSetup')}
            <Button
              icon
              className="sectionToggle"
              onClick={() => {
                this.setState({showSetup: !showSetup});
              }}
            >
              <Icon type={showSetup ? 'up' : 'down'} />
            </Button>
          </h3>
          <ul>
            {['view', 'groupBy'].map((field, idx, fields) => {
              const previous = fields
                .filter((prev, prevIdx) => prevIdx < idx)
                .map((prev) => data[prev]);

              return (
                <li className="select" key={field}>
                  <span className="label">{t(`report.${field}.label`)}</span>
                  <ReportSelect
                    type="decision"
                    field={field}
                    report={this.props.report}
                    value={data[field]}
                    variables={this.state.variables}
                    previous={previous}
                    disabled={!decisionDefinitionKey || previous.some((entry) => !entry)}
                    onChange={(newValue) => this.updateReport(field, newValue)}
                  />
                </li>
              );
            })}
          </ul>
        </section>
        <div className="filter header">
          <h3 className="sectionTitle">
            <Icon type="filter" />
            {t('common.filter.label')}
            <Button
              icon
              className="sectionToggle"
              onClick={() => {
                this.setState({showFilter: !showFilter});
              }}
            >
              <Icon type={showFilter ? 'up' : 'down'} />
            </Button>
            {filter?.length > 0 && <span className="filterCount">{filter.length}</span>}
          </h3>
        </div>
        <div
          className={classnames('scrollable', {withDivider: scrolled || !showFilter})}
          onScroll={(evt) => this.setState({scrolled: evt.target.scrollTop > 0})}
        >
          <section className={classnames('filter', {hidden: !showFilter})}>
            <DecisionFilter
              data={filter}
              onChange={this.props.updateReport}
              decisionDefinitionKey={decisionDefinitionKey}
              decisionDefinitionVersions={decisionDefinitionVersions}
              tenants={tenantIds}
              variables={this.state.variables}
            />
          </section>
        </div>
        {result && typeof result.instanceCount !== 'undefined' && (
          <div className="instanceCount">
            {t(
              `report.instanceCount.decision.label${
                result.instanceCountWithoutFilters !== 1 ? '-plural' : ''
              }`,
              {count: result.instanceCount, totalCount: result.instanceCountWithoutFilters}
            )}
          </div>
        )}
      </div>
    );
  }
}

export default withErrorHandling(DecisionControlPanel);
