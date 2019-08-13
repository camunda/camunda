/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import equal from 'deep-equal';

import {DefinitionSelection} from 'components';

import {Configuration} from './Configuration';
import ReportSelect from './ReportSelect';

import {DecisionFilter} from './filter';

import {reportConfig, loadDecisionDefinitionXml} from 'services';
import {loadInputVariables, loadOutputVariables} from './service';
import {t} from 'translation';

const {decision: decisionConfig} = reportConfig;

export default class DecisionControlPanel extends React.Component {
  state = {
    variables: {
      inputVariable: [],
      outputVariable: []
    }
  };

  componentDidMount() {
    this.loadVariables();
  }

  componentDidUpdate(prevProps) {
    const {data} = this.props.report;
    const {data: prevData} = prevProps.report;

    if (
      data.decisionDefinitionKey !== prevData.decisionDefinitionKey ||
      !equal(data.decisionDefinitionVersions, prevData.decisionDefinitionVersions) ||
      !equal(data.tenantIds, prevData.tenantIds)
    ) {
      this.loadVariables();
    }
  }

  loadVariables = async () => {
    const {decisionDefinitionKey, decisionDefinitionVersions, tenantIds} = this.props.report.data;
    if (decisionDefinitionKey && decisionDefinitionVersions && tenantIds) {
      const payload = {decisionDefinitionKey, decisionDefinitionVersions, tenantIds};
      this.setState({
        variables: {
          inputVariable: await loadInputVariables(payload),
          outputVariable: await loadOutputVariables(payload)
        }
      });
    }
  };

  changeDefinition = async (key, versions, tenants) => {
    const {groupBy, filter} = this.props.report.data;

    const change = {
      decisionDefinitionKey: {$set: key},
      decisionDefinitionVersions: {$set: versions},
      tenantIds: {$set: tenants},
      configuration: {
        excludedColumns: {$set: []},
        columnOrder: {
          $set: {
            inputVariables: [],
            instanceProps: [],
            outputVariables: [],
            variables: []
          }
        },
        xml: {
          $set:
            key && versions && versions[0]
              ? await loadDecisionDefinitionXml(key, versions[0], tenants[0])
              : null
        }
      },
      filter: {
        $set: filter.filter(({type}) => type !== 'inputVariable' && type !== 'outputVariable')
      }
    };

    if (groupBy && (groupBy.type === 'inputVariable' || groupBy.type === 'outputVariable')) {
      change.groupBy = {$set: null};
      change.visualization = {$set: null};
    }

    this.props.updateReport(change, true);
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
      visualization,
      configuration: {xml}
    } = data;

    return (
      <div className="DecisionControlPanel ReportControlPanel">
        <ul>
          <li className="select">
            <span className="label">{t('report.definition.decision')}</span>
            <DefinitionSelection
              type="decision"
              definitionKey={decisionDefinitionKey}
              versions={decisionDefinitionVersions}
              tenants={tenantIds}
              xml={xml}
              onChange={this.changeDefinition}
            />
          </li>
          {['view', 'groupBy', 'visualization'].map((field, idx, fields) => {
            const previous = fields
              .filter((prev, prevIdx) => prevIdx < idx)
              .map(prev => data[prev]);

            return (
              <li className="select" key={field}>
                <span className="label">{t(`report.${field}.label`)}</span>
                <ReportSelect
                  type="decision"
                  field={field}
                  value={data[field]}
                  variables={this.state.variables}
                  previous={previous}
                  disabled={!decisionDefinitionKey || previous.some(entry => !entry)}
                  onChange={newValue => this.updateReport(field, newValue)}
                />
              </li>
            );
          })}
          <li className="filter">
            <DecisionFilter
              data={filter}
              onChange={this.props.updateReport}
              instanceCount={result && result.decisionInstanceCount}
              decisionDefinitionKey={decisionDefinitionKey}
              decisionDefinitionVersions={decisionDefinitionVersions}
              tenants={tenantIds}
              variables={this.state.variables}
            />
          </li>
          <Configuration
            type={visualization}
            onChange={this.props.updateReport}
            report={this.props.report}
          />
        </ul>
      </div>
    );
  }
}
