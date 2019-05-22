/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import AnalysisControlPanel from './AnalysisControlPanel';
import {BPMNDiagram, Message} from 'components';

import {loadFrequencyData} from './service';
import {incompatibleFilters, loadProcessDefinitionXml} from 'services';
import DiagramBehavior from './DiagramBehavior';
import Statistics from './Statistics';

import './Analysis.scss';

export default class Analysis extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      config: {
        processDefinitionKey: '',
        processDefinitionVersion: '',
        tenantIds: [],
        filter: []
      },
      data: null,
      hoveredControl: null,
      hoveredNode: null,
      gateway: null,
      endEvent: null,
      xml: null
    };
  }

  render() {
    const {xml, config, hoveredControl, hoveredNode, gateway, endEvent, data} = this.state;

    return (
      <div className="Analysis">
        <AnalysisControlPanel
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
        {config.filter && incompatibleFilters(config.filter) && (
          <Message type="warning">
            No data is shown since the combination of filters is incompatible with each other
          </Message>
        )}
        <div className="content">
          <div className="Analysis__diagram">
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
    const procDefConfigured = config.processDefinitionKey && config.processDefinitionVersion;
    const procDefChanged =
      prevConfig.processDefinitionKey !== config.processDefinitionKey ||
      prevConfig.processDefinitionVersion !== config.processDefinitionVersion;
    if (procDefConfigured && (procDefChanged || prevConfig.filter !== config.filter)) {
      this.setState({
        data: await loadFrequencyData(
          config.processDefinitionKey,
          config.processDefinitionVersion,
          config.tenantIds,
          config.filter
        )
      });
    }
  }

  setViewer = viewer => {
    this.viewer = viewer;
  };

  updateHoveredControl = newField => {
    this.setState({hoveredControl: newField});
  };

  updateHoveredNode = newNode => {
    this.setState({hoveredNode: newNode});
  };

  updateSelection = (type, node) => {
    this.setState({[type]: node});
  };

  updateConfig = async updates => {
    if (updates.processDefinitionKey && updates.processDefinitionVersion && updates.tenantIds) {
      this.setState({
        config: {...this.state.config, ...updates},
        xml: await loadProcessDefinitionXml(
          updates.processDefinitionKey,
          updates.processDefinitionVersion,
          updates.tenantIds[0]
        ),
        gateway: null,
        endEvent: null
      });
    } else if (
      !updates.processDefinitionKey ||
      !updates.processDefinitionVersion ||
      !updates.tenantIds
    ) {
      this.setState({
        config: {...this.state.config, ...updates},
        xml: null,
        gateway: null,
        endEvent: null
      });
    }
  };
}
