/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import equal from 'deep-equal';
import {t} from 'translation';
import {loadProcessDefinitionXml, getFlowNodeNames} from 'services';
import {loadNodesOutliers, loadDurationData} from './service';
import {BPMNDiagram, HeatmapOverlay, Button} from 'components';
import OutlierControlPanel from './OutlierControlPanel';
import OutlierDetailsModal from './OutlierDetailsModal';
import InstancesButton from './InstancesButton';
import {withErrorHandling} from 'HOC';

import './OutlierAnalysis.scss';
import {showError} from 'notifications';

export class OutlierAnalysis extends React.Component {
  state = {
    config: {
      processDefinitionKey: '',
      processDefinitionVersions: [],
      tenantIds: [],
    },
    xml: null,
    data: {},
    heatData: {},
    flowNodeNames: {},
    selectedNode: null,
  };

  loadFlowNodeNames = (config) => {
    this.props.mightFail(
      getFlowNodeNames(
        config.processDefinitionKey,
        config.processDefinitionVersions[0],
        config.tenantIds[0]
      ),
      (flowNodeNames) => this.setState({flowNodeNames}),
      showError
    );
  };

  updateConfig = (updates) => {
    const newConfig = {...this.state.config, ...updates};

    if (updates.processDefinitionKey && updates.processDefinitionVersions && updates.tenantIds) {
      this.props.mightFail(
        loadProcessDefinitionXml(
          updates.processDefinitionKey,
          updates.processDefinitionVersions[0],
          updates.tenantIds[0]
        ),
        (xml) => this.setState({config: newConfig, xml}),
        showError
      );
    } else if (
      !newConfig.processDefinitionKey ||
      !newConfig.processDefinitionVersions ||
      !newConfig.tenantIds
    ) {
      this.setState({config: newConfig, xml: null});
    }
  };

  componentDidUpdate(_, prevState) {
    const {config} = this.state;
    const {config: prevConfig} = prevState;
    const procDefConfigured = config.processDefinitionKey && config.processDefinitionVersions;
    const procDefChanged =
      prevConfig.processDefinitionKey !== config.processDefinitionKey ||
      !equal(prevConfig.processDefinitionVersions, config.processDefinitionVersions);
    const tenantsChanged = !equal(prevConfig.tenantIds, config.tenantIds);
    if (procDefConfigured && (procDefChanged || tenantsChanged)) {
      this.loadOutlierData(config);
    }
  }

  loadOutlierData = (config) => {
    this.loadFlowNodeNames(config);
    this.props.mightFail(
      loadNodesOutliers(config),
      (data) => {
        const heatData = Object.keys(data).reduce(
          (acc, key) => ({...acc, [key]: data[key].higherOutlierHeat || undefined}),
          {}
        );

        this.setState({
          data,
          heatData,
        });
      },
      showError
    );
  };

  renderTooltip = (data, id) => {
    const {flowNodeNames, config} = this.state;
    const nodeData = this.state.data[id];
    if (!data || !nodeData.higherOutlier) {
      return undefined;
    }
    const {
      higherOutlier: {count, relation, boundValue},
      totalCount,
    } = nodeData;

    return (
      <div className="nodeTooltip">
        <div className="tooltipTitle">
          <b>{flowNodeNames[id] || id} :</b> {t('analysis.outlier.totalInstances')} {totalCount}
        </div>
        <p className="description">
          {t(`analysis.outlier.tooltipText.${count === 1 ? 'singular' : 'plural'}`, {
            count,
            percentage: Math.round(relation * 100),
          })}
        </p>
        <Button onClick={() => this.loadChartData(id, nodeData)}>{t('common.viewDetails')}</Button>
        <InstancesButton id={id} name={flowNodeNames[id]} value={boundValue} config={config} />
      </div>
    );
  };

  loadChartData = (id, nodeData) => {
    this.setState({loading: true});
    this.props.mightFail(
      loadDurationData({
        ...this.state.config,
        flowNodeId: id,
        higherOutlierBound: nodeData.higherOutlier.boundValue,
      }),
      (data) => {
        this.setState({
          loading: false,
          selectedNode: {
            name: this.state.flowNodeNames[id] || id,
            id,
            data,
            ...nodeData,
          },
        });
      }
    );
  };

  render() {
    const {xml, config, heatData, loading} = this.state;
    return (
      <div className="OutlierAnalysis">
        <OutlierControlPanel {...config} onChange={this.updateConfig} xml={xml} />
        <div className="OutlierAnalysis__diagram">
          {xml && (
            <BPMNDiagram xml={xml} loading={loading}>
              <HeatmapOverlay
                tooltipOptions={{theme: 'light'}}
                noSequenceHighlight
                data={heatData}
                formatter={this.renderTooltip}
              />
            </BPMNDiagram>
          )}
        </div>
        {this.state.selectedNode && (
          <OutlierDetailsModal
            onClose={() => this.setState({selectedNode: null})}
            selectedNode={this.state.selectedNode}
            config={config}
          />
        )}
      </div>
    );
  }
}

export default withErrorHandling(OutlierAnalysis);
