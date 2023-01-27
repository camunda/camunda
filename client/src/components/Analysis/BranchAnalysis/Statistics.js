/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import equal from 'fast-deep-equal';
import {Chart as ChartRenderer} from 'chart.js';

import {loadCorrelationData} from './service';

import {getFlowNodeNames, getDiagramElementsBetween} from 'services';

import './Statistics.scss';
import {LoadingIndicator} from 'components';
import {t} from 'translation';

export default class Statistics extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      data: null,
      flowNodeNames: null,
    };

    this.rootRef = React.createRef();
  }

  loadFlowNodeNames = () => {
    return new Promise(async (resolve) =>
      this.setState(
        {
          flowNodeNames: await getFlowNodeNames(
            this.props.config.processDefinitionKey,
            this.props.config.processDefinitionVersions[0],
            this.props.config.tenantIds[0]
          ),
        },
        resolve
      )
    );
  };

  render() {
    if (this.state.data && this.props.gateway && this.props.endEvent) {
      const totalGateway = Object.keys(this.state.data.followingNodes).reduce(
        (prev, key) => prev + this.state.data.followingNodes[key].activityCount,
        0
      );

      if (!totalGateway) {
        return (
          <div className="Statistics">
            <div className="placeholder">{t('analysis.noInstances')}</div>
          </div>
        );
      }

      return (
        <div className="Statistics" ref={this.rootRef}>
          <p>{t('analysis.gatewayInstances', {totalGateway})}</p>
          <ul>
            {Object.keys(this.state.data.followingNodes).map((key) => {
              const count = this.state.data.followingNodes[key].activityCount;
              const reached = this.state.data.followingNodes[key].activitiesReached;

              return (
                <li key={key}>
                  {t('analysis.branchDistribution', {
                    count,
                    branchPercentage: Math.round((count / totalGateway) * 100) || 0,
                    reached,
                    reachedEndPercentage: Math.round((reached / count) * 100) || 0,
                  })}
                </li>
              );
            })}
          </ul>
          <p>{t('analysis.gatewayDistribution')}</p>
          <div className="diagram-container">
            <canvas ref={(node) => (this.absoluteChartRef = node)} />
          </div>
          <p>{t('analysis.endEventProbability')}</p>
          <div className="diagram-container">
            <canvas ref={(node) => (this.relativeChartRef = node)} />
          </div>
        </div>
      );
    }

    if (this.props.gateway && this.props.endEvent && !this.state.data) {
      return (
        <div className="Statistics">
          <LoadingIndicator />
        </div>
      );
    }

    return (
      <div className="Statistics">
        <div className="placeholder">{t('analysis.instructionMessage')}</div>
      </div>
    );
  }

  async componentDidUpdate(prevProps) {
    this.fillFlownodeNames();

    const procDefChanged =
      prevProps.config.processDefinitionKey !== this.props.config.processDefinitionKey ||
      !equal(
        prevProps.config.processDefinitionVersions,
        this.props.config.processDefinitionVersions
      );

    if (procDefChanged) {
      await this.loadFlowNodeNames();
    }

    const selectionChanged =
      (prevProps.gateway !== this.props.gateway ||
        prevProps.endEvent !== this.props.endEvent ||
        prevProps.config.filter !== this.props.config.filter) &&
      this.props.gateway &&
      this.props.endEvent;

    if (selectionChanged) {
      await this.loadCorrelation();
      this.createCharts();
    }
  }

  fillFlownodeNames = () => {
    const container = this.rootRef.current;

    if (container) {
      const gatewayName = this.props.gateway.name || this.props.gateway.id;
      const endEventName = this.props.endEvent.name || this.props.endEvent.id;
      const branchNames = Object.keys(this.state.data.followingNodes);

      container
        .querySelectorAll('.gatewayName')
        .forEach((node) => (node.textContent = gatewayName));
      container
        .querySelectorAll('.endEventName')
        .forEach((node) => (node.textContent = endEventName));
      container
        .querySelectorAll('.branchName')
        .forEach((node, i) => (node.textContent = branchNames[i]));
    }
  };

  createCharts = () => {
    if (this.relativeChartRef && this.absoluteChartRef) {
      if (this.relativeChart) {
        this.relativeChart.destroy();
      }
      this.relativeChart = this.createChart(
        this.relativeChartRef,
        ({activitiesReached, activityCount}) => {
          return Math.round((activitiesReached / activityCount) * 1000) / 10 || 0;
        },
        '%'
      );

      if (this.absoluteChart) {
        this.absoluteChart.destroy();
      }
      this.absoluteChart = this.createChart(this.absoluteChartRef, ({activityCount}) => {
        return activityCount || 0;
      });
    }
  };

  loadCorrelation = () => {
    return new Promise((resolve) => {
      this.setState(
        {
          data: null,
        },
        async () => {
          this.setState(
            {
              data: await loadCorrelationData(
                this.props.config.processDefinitionKey,
                this.props.config.processDefinitionVersions,
                this.props.config.tenantIds,
                this.props.config.filter,
                this.props.gateway.id,
                this.props.endEvent.id
              ),
            },
            async () => {
              await this.applyFlowNodeNames();
              resolve();
            }
          );
        }
      );
    });
  };

  applyFlowNodeNames = () => {
    return new Promise((resolve) => {
      const nodes = this.state.data.followingNodes;
      const flowNodeNames = this.state.flowNodeNames;
      const chartData = {};
      Object.keys(nodes).forEach((v) => {
        const sequenceFlow = this.props.gateway.outgoing.find(({targetRef: {id}}) => id === v);
        chartData[sequenceFlow.name || flowNodeNames[v] || v] = nodes[v];
      });

      this.setState(
        {
          data: {
            followingNodes: chartData,
          },
        },
        resolve
      );
    });
  };

  createChart = (node, dataFct, labelSuffix = '') => {
    let isInside = false;
    const {viewer} = this.props;
    return new ChartRenderer(node, {
      type: 'bar',
      data: {
        labels: Object.keys(this.state.data.followingNodes),
        datasets: [
          {
            data: Object.values(this.state.data.followingNodes).map(dataFct),
            borderColor: '#1991c8',
            backgroundColor: '#1991c8',
            borderWidth: 2,
          },
        ],
      },
      options: {
        responsive: true,
        animation: false,
        maintainAspectRatio: false,
        indexAxis: 'y',
        plugins: {
          legend: {
            display: false,
          },
          tooltip: {
            callbacks: {
              label: ({dataset, dataIndex}) => dataset.data[dataIndex] + labelSuffix,
            },
          },
        },
        hover: {
          onHover: (e, activeElements) => {
            const canvas = viewer.get('canvas');
            const classMark = 'PartHighlight';
            if (activeElements.length > 0 && !isInside) {
              // triggered once the mouse move from outside to inside a bar box
              this.markSequenceFlow(viewer, canvas, activeElements, classMark);
              isInside = true;
              viewer._container.classList.add('highlight-single-path');
            } else if (activeElements.length <= 0 && isInside) {
              // triggered once the mouse move from inside to outside the barchart box
              const elementRegistry = viewer.get('elementRegistry');
              elementRegistry.forEach((element) => canvas.removeMarker(element, classMark));
              isInside = false;
              viewer._container.classList.remove('highlight-single-path');
            }
          },
        },
        scales: {
          x: [
            {
              ticks: {
                beginAtZero: true,
                callback: (...args) => ChartRenderer.Ticks.formatters.linear(...args) + labelSuffix,
              },
            },
          ],
        },
      },
    });
  };

  markSequenceFlow = (viewer, canvas, activeElements, classMark) => {
    const {gateway, endEvent} = this.props;
    const hoveredElementLabel = activeElements[0]._model.label;
    const sequenceFlow = gateway.outgoing.find(
      (element) =>
        element.name === hoveredElementLabel ||
        element.targetRef.name === hoveredElementLabel ||
        element.targetRef.id === hoveredElementLabel
    );
    if (sequenceFlow) {
      canvas.addMarker(gateway, classMark);
      canvas.addMarker(sequenceFlow, classMark);
      const reachableNodes = getDiagramElementsBetween(sequenceFlow.targetRef, endEvent, viewer);

      reachableNodes.forEach((id) => canvas.addMarker(id, classMark));
    }
  };
}
