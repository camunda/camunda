/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import equal from 'fast-deep-equal';
import {InlineNotification} from '@carbon/react';

import {BPMNDiagram, PageTitle} from 'components';
import {incompatibleFilters, loadProcessDefinitionXml} from 'services';
import {t} from 'translation';
import {withDocs, withErrorHandling} from 'HOC';
import {showError} from 'notifications';
import {track} from 'tracking';

import DiagramBehavior from './DiagramBehavior';
import Statistics from './Statistics';
import BranchControlPanel from './BranchControlPanel';
import {loadFrequencyData} from './service';

import './BranchAnalysis.scss';

export class BranchAnalysis extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      config: {
        processDefinitionKey: '',
        processDefinitionVersions: [],
        identifier: 'definition',
        tenantIds: [],
        filters: [],
      },
      data: null,
      hoveredControl: null,
      hoveredNode: null,
      gateway: null,
      endEvent: null,
      optimizeVersion: 'latest',
      xml: null,
    };
  }

  render() {
    const {xml, config, hoveredControl, hoveredNode, gateway, endEvent, data} = this.state;

    return (
      <div className="BranchAnalysis">
        <PageTitle pageName={t('analysis.branchAnalysis')} />
        <BranchControlPanel
          {...config}
          hoveredControl={hoveredControl}
          hoveredNode={hoveredNode}
          onChange={this.updateConfig}
          gateway={gateway}
          endEvent={endEvent}
          updateHover={this.updateHoveredControl}
          updateSelection={this.updateSelection}
          xml={xml}
        />
        {config.filters && incompatibleFilters(config.filters) && (
          <InlineNotification
            kind="warning"
            hideCloseButton
            subtitle={t('common.filter.incompatibleFilters')}
            className="incompatibleFiltersWarning"
          />
        )}
        <div className="content">
          <div className="BranchAnalysis__diagram">
            {xml && (
              <BPMNDiagram xml={xml}>
                <DiagramBehavior
                  hoveredControl={hoveredControl}
                  hoveredNode={hoveredNode}
                  updateHover={this.updateHoveredNode}
                  updateSelection={this.updateSelection}
                  gateway={gateway}
                  endEvent={endEvent}
                  data={data}
                  setViewer={this.setViewer}
                />
              </BPMNDiagram>
            )}
          </div>
          <Statistics gateway={gateway} endEvent={endEvent} config={config} viewer={this.viewer} />
        </div>
      </div>
    );
  }

  async componentDidUpdate(_, prevState) {
    const {config} = this.state;
    const {config: prevConfig} = prevState;
    const procDefConfigured = config.processDefinitionKey && config.processDefinitionVersions;
    const procDefChanged =
      prevConfig.processDefinitionKey !== config.processDefinitionKey ||
      !equal(prevConfig.processDefinitionVersions, config.processDefinitionVersions);
    const tenantsChanged = !equal(prevConfig.tenantIds, config.tenantIds);
    const filterChanged = !equal(prevConfig.filters, config.filters);

    if (procDefConfigured && (procDefChanged || tenantsChanged || filterChanged)) {
      this.setState({
        data: await loadFrequencyData(
          config.processDefinitionKey,
          config.processDefinitionVersions,
          config.tenantIds,
          config.identifier,
          config.filters
        ),
      });
    }
  }

  setViewer = (viewer) => {
    this.viewer = viewer;
  };

  updateHoveredControl = (newField) => {
    this.setState({hoveredControl: newField});
  };

  updateHoveredNode = (newNode) => {
    this.setState({hoveredNode: newNode});
  };

  updateSelection = (type, node) => {
    this.setState({[type]: node});
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
          track('startBranchAnalysis', {processDefinitionKey});
        },
        showError
      );

      if (changes.xml !== this.state.xml) {
        changes.gateway = null;
        changes.endEvent = null;
      }
    } else if (
      !newConfig.processDefinitionKey ||
      !newConfig.processDefinitionVersions ||
      !newConfig.tenantIds
    ) {
      changes.xml = null;
      changes.gateway = null;
      changes.endEvent = null;
    }

    this.setState(changes);
  };
}

export default withErrorHandling(withDocs(BranchAnalysis));
