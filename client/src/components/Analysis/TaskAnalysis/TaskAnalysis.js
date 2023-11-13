/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import equal from 'fast-deep-equal';
import classnames from 'classnames';

import {t} from 'translation';
import {showError} from 'notifications';
import {BPMNDiagram, HeatmapOverlay, MessageBox, PageTitle} from 'components';
import {loadProcessDefinitionXml, getFlowNodeNames, incompatibleFilters} from 'services';
import {withErrorHandling, withUser} from 'HOC';
import {track} from 'tracking';

import OutlierControlPanel from './OutlierControlPanel';
import OutlierDetailsModal from './OutlierDetailsModal';
import {loadNodesOutliers, loadDurationData} from './service';

import './TaskAnalysis.scss';

export class TaskAnalysis extends React.Component {
  state = {
    config: {
      processDefinitionKey: '',
      processDefinitionVersions: [],
      tenantIds: [],
      minimumDeviationFromAvg: 50,
      disconsiderAutomatedTasks: false,
      filters: [],
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

  updateConfig = async (updates) => {
    const newConfig = {...this.state.config, ...updates};
    const {processDefinitionKey, processDefinitionVersions, tenantIds} = updates;

    const changes = {
      config: newConfig,
    };

    if (processDefinitionKey && processDefinitionVersions && tenantIds) {
      await this.props.mightFail(
        loadProcessDefinitionXml(processDefinitionKey, processDefinitionVersions[0], tenantIds[0]),
        (xml) => {
          changes.xml = xml;
          track('startOutlierAnalysis', {processDefinitionKey});
        },
        showError
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

  componentDidUpdate(_, prevState) {
    const {config} = this.state;
    const {config: prevConfig} = prevState;
    const procDefConfigured = config.processDefinitionKey && config.processDefinitionVersions;
    const procDefChanged =
      prevConfig.processDefinitionKey !== config.processDefinitionKey ||
      !equal(prevConfig.processDefinitionVersions, config.processDefinitionVersions);
    const tenantsChanged = !equal(prevConfig.tenantIds, config.tenantIds);
    const filtersChanged = !equal(prevConfig.filters, config.filters);

    if (procDefConfigured && (procDefChanged || tenantsChanged || filtersChanged)) {
      this.loadOutlierData(config);
    }
    this.indicateClickableNodes();
  }

  indicateClickableNodes = () => {
    const {heatData} = this.state;
    if (heatData) {
      Object.keys(heatData).forEach((id) => {
        const node = document.body.querySelector(`[data-element-id=${id}]`);
        node?.classList.add('clickable');
      });
    }
  };

  loadOutlierData = (config) => {
    this.setState({loading: true, heatData: {}});
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
      showError,
      () => this.setState({loading: false})
    );
  };

  onNodeClick = ({element: {id}}) => {
    const nodeData = this.state.data[id];
    if (!nodeData?.higherOutlier) {
      return;
    }
    this.loadChartData(id, nodeData);
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
          selectedNode: {
            name: this.state.flowNodeNames[id] || id,
            id,
            data,
            ...nodeData,
          },
        });
      },
      undefined,
      () => this.setState({loading: false})
    );
  };

  render() {
    const {xml, config, heatData, loading} = this.state;
    const empty = xml && !loading && Object.keys(heatData).length === 0;

    return (
      <div className="TaskAnalysis">
        <PageTitle pageName={t('analysis.task.label')} />
        <OutlierControlPanel {...config} onChange={this.updateConfig} xml={xml} />
        {config.filters && incompatibleFilters(config.filters) && (
          <MessageBox type="warning">{t('common.filter.incompatibleFilters')}</MessageBox>
        )}
        <div className={classnames('TaskAnalysis__diagram', {empty})}>
          {xml && (
            <BPMNDiagram xml={xml} loading={loading}>
              <HeatmapOverlay data={heatData} onNodeClick={this.onNodeClick} />
            </BPMNDiagram>
          )}
          {empty && <div className="noOutliers">{t('analysis.task.notFound')}</div>}
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

export default withErrorHandling(withUser(TaskAnalysis));
