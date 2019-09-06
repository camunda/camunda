/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {Component} from 'react';

import OutlierControlPanel from './OutlierControlPanel';
import {loadProcessDefinitionXml, getFlowNodeNames} from 'services';
import {BPMNDiagram, HeatmapOverlay, Button} from 'components';
import ReactDOM from 'react-dom';
import equal from 'deep-equal';

import {loadNodesOutliers, loadDurationData} from './service';
import OutlierDetailsModal from './OutlierDetailsModal';
import {t} from 'translation';

import './OutlierAnalysis.scss';

export default class OutlierAnalysis extends Component {
  state = {
    config: {
      processDefinitionKey: '',
      processDefinitionVersions: [],
      tenantIds: []
    },
    xml: null,
    data: {},
    heatData: {},
    flowNodeNames: {},
    selectedNode: null
  };

  loadFlowNodeNames = async config => {
    this.setState({
      flowNodeNames: await getFlowNodeNames(
        config.processDefinitionKey,
        config.processDefinitionVersions[0],
        config.tenantIds[0]
      )
    });
  };

  updateConfig = async updates => {
    const newConfig = {...this.state.config, ...updates};

    const changes = {
      config: newConfig
    };

    if (updates.processDefinitionKey && updates.processDefinitionVersions && updates.tenantIds) {
      changes.xml = await loadProcessDefinitionXml(
        updates.processDefinitionKey,
        updates.processDefinitionVersions[0],
        updates.tenantIds[0]
      );
    } else if (
      !newConfig.processDefinitionKey ||
      !newConfig.processDefinitionVersions ||
      !newConfig.tenantIds
    ) {
      changes.xml = null;
    }

    this.setState(changes);
  };

  async componentDidUpdate(_, prevState) {
    const {config} = this.state;
    const {config: prevConfig} = prevState;
    const procDefConfigured = config.processDefinitionKey && config.processDefinitionVersions;
    const procDefChanged =
      prevConfig.processDefinitionKey !== config.processDefinitionKey ||
      !equal(prevConfig.processDefinitionVersions, config.processDefinitionVersions);
    const tenantsChanged = !equal(prevConfig.tenantIds, config.tenantIds);
    if (procDefConfigured && (procDefChanged || tenantsChanged)) {
      await this.loadOutlierData(config);
    }
  }

  loadOutlierData = async config => {
    this.loadFlowNodeNames(config);
    const data = await loadNodesOutliers(config);
    const heatData = Object.keys(data).reduce(
      (acc, key) => ({...acc, [key]: data[key].higherOutlierHeat || undefined}),
      {}
    );

    this.setState({
      data,
      heatData
    });
  };

  renderTooltip = (id, data) => {
    const nodeData = this.state.data[id];
    if (!data || !nodeData.higherOutlier) {
      return undefined;
    }

    return (
      <>
        <div className="tooltipTitle">
          <b>{this.state.flowNodeNames[id] || id} :</b> {t('analysis.outlier.totalInstances')}{' '}
          {nodeData.totalCount}
        </div>
        <p className="description">
          {t('analysis.outlier.tooltipText', {
            count: nodeData.higherOutlier.count,
            instance: t(
              `analysis.outlier.tooltip.instance.label${
                nodeData.higherOutlier.count === 1 ? '' : '-plural'
              }`
            ),
            percentage: Math.round(nodeData.higherOutlier.relation * 100)
          })}
        </p>
        <Button onClick={() => this.loadChartData(id, nodeData)}>{t('common.viewDetails')}</Button>
      </>
    );
  };

  loadChartData = async (id, nodeData) => {
    const data = await loadDurationData({
      ...this.state.config,
      flowNodeId: id,
      higherOutlierBound: nodeData.higherOutlier.boundValue
    });

    this.setState({
      selectedNode: {
        name: this.state.flowNodeNames[id] || id,
        id,
        data,
        ...nodeData
      }
    });
  };

  render() {
    const {xml, config, heatData} = this.state;
    return (
      <div className="OutlierAnalysis">
        <OutlierControlPanel {...config} onChange={this.updateConfig} xml={xml} />
        <div className="OutlierAnalysis__diagram">
          {xml && (
            <BPMNDiagram xml={xml}>
              <HeatmapOverlay
                noSequenceHighlight
                data={heatData}
                formatter={(data, id) => {
                  const div = document.createElement('div');
                  div.className = 'nodeTooltip';
                  ReactDOM.render(this.renderTooltip(id, data), div);
                  return div;
                }}
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
