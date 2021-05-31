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
    showSource: true,
    showSetup: true,
    showFilter: false,
  };

  componentDidMount() {
    this.loadVariables(this.props.report.data?.definitions?.[0]);
  }

  loadVariables = ({key, versions, tenantIds}) => {
    if (key && versions && tenantIds) {
      const payload = [
        {decisionDefinitionKey: key, decisionDefinitionVersions: versions, tenantIds},
      ];
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

  loadXml = ({key, versions, tenantIds}) => {
    if (key && versions?.[0] && tenantIds) {
      return new Promise((resolve, reject) => {
        this.props.mightFail(
          loadDecisionDefinitionXml(key, versions[0], tenantIds[0]),
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
      key,
      versions,
      tenantIds,
      name,
      displayName: name,
    };

    this.props.setLoading(true);
    const [xml] = await Promise.all([
      this.loadXml(definitionData),
      this.loadVariables(definitionData),
    ]);

    const change = {
      definitions: {$set: [definitionData]},
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
      definitions,
      filter,
      configuration: {xml},
    } = data;
    const {showSource, showSetup, showFilter} = this.state;

    const {key, versions, tenantIds} = definitions?.[0] ?? {};

    return (
      <div className="DecisionControlPanel ReportControlPanel">
        <div className="controlSections" style={{overflow: 'initial'}}>
          {/* manual style override will be removed once decision reports use multi-definition setup */}
          <section className={classnames('select', 'source', {hidden: !showSource})}>
            <Button
              className="sectionTitle"
              onClick={() => {
                this.setState({showSource: !showSource});
              }}
            >
              <Icon type="data-source" />
              {t('common.dataSource')}
              <span className={classnames('sectionToggle', {open: showSource})}>
                <Icon type="down" />
              </span>
            </Button>
            <DefinitionSelection
              type="decision"
              definitionKey={key}
              versions={versions}
              tenants={tenantIds}
              xml={xml}
              onChange={this.changeDefinition}
            />
          </section>
          <section className={classnames('reportSetup', {hidden: !showSetup})}>
            <Button
              className="sectionTitle"
              onClick={() => {
                this.setState({showSetup: !showSetup});
              }}
            >
              <Icon type="report" />
              {t('report.reportSetup')}
              <span className={classnames('sectionToggle', {open: showSetup})}>
                <Icon type="down" />
              </span>
            </Button>
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
                      disabled={!key || previous.some((entry) => !entry)}
                      onChange={(newValue) => this.updateReport(field, newValue)}
                    />
                  </li>
                );
              })}
            </ul>
          </section>
          <section className={classnames('filter', {hidden: !showFilter})}>
            <Button
              className="sectionTitle"
              onClick={() => {
                this.setState({showFilter: !showFilter});
              }}
            >
              <Icon type="filter" />
              {t('common.filter.label')}
              <span className={classnames('sectionToggle', {open: showFilter})}>
                <Icon type="down" />
              </span>
              {filter?.length > 0 && <span className="filterCount">{filter.length}</span>}
            </Button>
            <DecisionFilter
              data={filter}
              onChange={this.props.updateReport}
              decisionDefinitionKey={key}
              decisionDefinitionVersions={versions}
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
              }-withFilter`,
              {count: result.instanceCount, totalCount: result.instanceCountWithoutFilters}
            )}
          </div>
        )}
      </div>
    );
  }
}

export default withErrorHandling(DecisionControlPanel);
