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
import {BPMNDiagram, HeatmapOverlay, Button, PageTitle} from 'components';
import {loadProcessDefinitionXml, getFlowNodeNames} from 'services';
import {withErrorHandling, withUser} from 'HOC';

import OutlierControlPanel from './OutlierControlPanel';
import OutlierDetailsModal from './OutlierDetailsModal';
import InstancesButton from './InstancesButton';
import {loadNodesOutliers, loadDurationData} from './service';

import './OutlierAnalysis.scss';

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

  updateConfig = async (updates) => {
    const newConfig = {...this.state.config, ...updates};

    if (updates.processDefinitionKey && updates.processDefinitionVersions && updates.tenantIds) {
      await this.props.mightFail(
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
        <InstancesButton
          id={id}
          name={flowNodeNames[id]}
          value={boundValue}
          config={config}
          totalCount={totalCount}
          user={this.props.user}
        />
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
      <div className="OutlierAnalysis">
        <PageTitle pageName={t('analysis.outlier.label')} />
        <OutlierControlPanel {...config} onChange={this.updateConfig} xml={xml} />
        <div className={classnames('OutlierAnalysis__diagram', {empty})}>
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
          {empty && <div className="noOutliers">{t('analysis.outlier.notFound')}</div>}
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

export default withErrorHandling(withUser(OutlierAnalysis));
