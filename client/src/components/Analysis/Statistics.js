/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import ChartRenderer from 'chart.js';

import {loadCorrelationData} from './service';

import {getFlowNodeNames, getDiagramElementsBetween} from 'services';

import './Statistics.scss';
import {LoadingIndicator} from 'components';

export default class Statistics extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      data: null,
      flowNodeNames: null
    };
  }

  loadFlowNodeNames = () => {
    return new Promise(async resolve =>
      this.setState(
        {
          flowNodeNames: await getFlowNodeNames(
            this.props.config.processDefinitionKey,
            this.props.config.processDefinitionVersion
          )
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
      const gatewayName = this.props.gateway.name || this.props.gateway.id;
      const endEventName = this.props.endEvent.name || this.props.endEvent.id;

      if (!totalGateway) {
        return (
          <div className="Statistics">
            <div className="placeholder">
              No Instances in the current filter passed the selected Gateway.
            </div>
          </div>
        );
      }

      return (
        <div className="Statistics">
          <p>
            Of all <b>{totalGateway} instances</b> that passed the Gateway <i>{gatewayName}</i>
          </p>
          <ul>
            {Object.keys(this.state.data.followingNodes).map(key => {
              const count = this.state.data.followingNodes[key].activityCount;
              const reached = this.state.data.followingNodes[key].activitiesReached;

              return (
                <li key={key}>
                  <b>{count}</b> ({Math.round((count / totalGateway) * 100) || 0}%) took the{' '}
                  <i>{key}</i> branch, <b>{reached}</b> ({Math.round((reached / count) * 100) || 0}
                  %) of those then continued to reach the end event <i>{endEventName}</i>
                </li>
              );
            })}
          </ul>
          <p>
            Distribution of Instances at the Gateway <i>{gatewayName}</i>:
          </p>
          <div className="diagram-container">
            <canvas ref={node => (this.absoluteChartRef = node)} />
          </div>
          <p>
            Probability to reach the end event <i>{endEventName}</i> after taking a branch:
          </p>
          <div className="diagram-container">
            <canvas ref={node => (this.relativeChartRef = node)} />
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
        <div className="placeholder">
          Please select a Process Definition, a Gateway and an End Event to perform the Analysis.
        </div>
      </div>
    );
  }

  async componentDidUpdate(prevProps) {
    const procDefChanged =
      prevProps.config.processDefinitionKey !== this.props.config.processDefinitionKey ||
      prevProps.config.processDefinitionVersion !== this.props.config.processDefinitionVersion;

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
    return new Promise(resolve => {
      this.setState(
        {
          data: null
        },
        async () => {
          this.setState(
            {
              data: await loadCorrelationData(
                this.props.config.processDefinitionKey,
                this.props.config.processDefinitionVersion,
                this.props.config.tenantIds,
                this.props.config.filter,
                this.props.gateway.id,
                this.props.endEvent.id
              )
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
    return new Promise(resolve => {
      const nodes = this.state.data.followingNodes;
      const flowNodeNames = this.state.flowNodeNames;
      const chartData = {};
      Object.keys(nodes).forEach(v => {
        const sequenceFlow = this.props.gateway.outgoing.find(({targetRef: {id}}) => id === v);
        chartData[sequenceFlow.name || flowNodeNames[v] || v] = nodes[v];
      });

      this.setState(
        {
          data: {
            followingNodes: chartData
          }
        },
        resolve
      );
    });
  };

  createChart = (node, dataFct, labelSuffix = '') => {
    let isInside = false;
    const {viewer} = this.props;
    return new ChartRenderer(node, {
      type: 'horizontalBar',
      data: {
        labels: Object.keys(this.state.data.followingNodes),
        datasets: [
          {
            data: Object.values(this.state.data.followingNodes).map(dataFct),
            borderColor: '#1991c8',
            backgroundColor: '#1991c8',
            borderWidth: 2
          }
        ]
      },
      options: {
        responsive: true,
        animation: false,
        maintainAspectRatio: false,
        legend: {
          display: false
        },
        tooltips: {
          callbacks: {
            label: ({index, datasetIndex}, {datasets}) =>
              datasets[datasetIndex].data[index] + labelSuffix
          }
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
              elementRegistry.forEach(element => canvas.removeMarker(element, classMark));
              isInside = false;
              viewer._container.classList.remove('highlight-single-path');
            }
          }
        },
        scales: {
          xAxes: [
            {
              ticks: {
                beginAtZero: true,
                callback: (...args) => ChartRenderer.Ticks.formatters.linear(...args) + labelSuffix
              }
            }
          ]
        }
      }
    });
  };

  markSequenceFlow = (viewer, canvas, activeElements, classMark) => {
    const {gateway, endEvent} = this.props;
    const hoveredElementLabel = activeElements[0]._model.label;
    const sequenceFlow = gateway.outgoing.find(
      element =>
        element.name === hoveredElementLabel ||
        element.targetRef.name === hoveredElementLabel ||
        element.targetRef.id === hoveredElementLabel
    );
    if (sequenceFlow) {
      canvas.addMarker(gateway, classMark);
      canvas.addMarker(sequenceFlow, classMark);
      const reachableNodes = getDiagramElementsBetween(sequenceFlow.targetRef, endEvent, viewer);

      reachableNodes.forEach(id => canvas.addMarker(id, classMark));
    }
  };
}
