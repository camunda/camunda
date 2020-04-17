/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import './DecisionTable.scss';
import 'dmn-js/dist/assets/dmn-js-decision-table.css';

import {themed} from 'theme';
import {formatters} from 'services';

import Viewer from 'dmn-js';
import createHitsColumnAddon from './HitsColumnAddon';
import DmnJsPortal from './DmnJsPortal';
import {migrateDiagram} from '@bpmn-io/dmn-migrate';
import {withErrorHandling} from 'HOC';
import {showError} from 'notifications';

export class DecisionTable extends React.Component {
  state = {
    entryPoints: {rules: {}},
  };

  container = React.createRef();

  componentDidMount() {
    const {
      configuration: {xml},
      decisionDefinitionKey,
    } = this.props.report.data;

    this.loadXML(xml, decisionDefinitionKey);
  }

  componentDidUpdate({
    report: {
      data: {
        decisionDefinitionKey: prevKey,
        configuration: {xml: prevXml},
      },
    },
  }) {
    const {
      configuration: {xml},
      decisionDefinitionKey,
    } = this.props.report.data;

    if (prevXml !== xml || prevKey !== decisionDefinitionKey) {
      this.loadXML(xml, decisionDefinitionKey);
    }
  }

  loadXML = async (xml, decisionDefinitionKey) => {
    const {entryPoints, Addon: HitsColumn} = createHitsColumnAddon();

    if (this.viewer) {
      this.viewer.destroy();
    }

    this.viewer = new Viewer({
      container: this.container.current,
      decisionTable: {additionalModules: [HitsColumn]},
    });

    const dmn13Xml = await this.migrateDiagram(xml);
    this.viewer.importXML(dmn13Xml, () => {
      this.viewer.open(
        this.viewer
          .getViews()
          .find(
            ({type, element: {id}}) => type === 'decisionTable' && id === decisionDefinitionKey
          ),
        () => this.setState({entryPoints})
      );
    });
  };

  migrateDiagram = (xml) => {
    return new Promise((resolve, reject) => {
      this.props.mightFail(migrateDiagram(xml), resolve, (error) => reject(showError(error)));
    });
  };

  renderRuleCell = (ruleId) => {
    const {
      result: {data, instanceCount},
      data: {
        configuration: {hideAbsoluteValue, hideRelativeValue, showGradientBars},
      },
    } = this.props.report;

    const resultObj = formatters.objectifyResult(data);

    const resultNumber = resultObj[ruleId] || 0;
    const percentage = Math.round((resultNumber / instanceCount) * 1000) / 10 || 0;

    const node = this.state.entryPoints.rules[ruleId];
    if (showGradientBars) {
      const progress = resultNumber / instanceCount;

      node.style.background = `linear-gradient(to right, ${getColor(
        this.props.theme,
        0
      )} 0%, ${getColor(this.props.theme, progress)} ${percentage}%, transparent ${percentage}%)`;
    } else {
      node.style.background = '';
    }

    let outputString = `${resultNumber} (${percentage}%)`;
    if (hideAbsoluteValue && hideRelativeValue) {
      outputString = '';
    } else if (hideAbsoluteValue) {
      outputString = percentage + '%';
    } else if (hideRelativeValue) {
      outputString = resultNumber;
    }

    return (
      <DmnJsPortal key={ruleId} renderIn={this.state.entryPoints.rules[ruleId]}>
        <i>{outputString}</i>
      </DmnJsPortal>
    );
  };

  render() {
    const {rules, summary} = this.state.entryPoints;
    const {
      result: {instanceCount, data},
    } = this.props.report;

    const hitCount = data.map(({value}) => value).reduce((sum, current) => sum + current, 0);

    return (
      <div ref={this.container} className="DecisionTable">
        {Object.keys(rules).map(this.renderRuleCell)}
        <DmnJsPortal renderIn={summary}>
          <b>
            {instanceCount} Evaluation{instanceCount !== 1 ? 's' : ''}
            {hitCount > instanceCount && ` / ${hitCount} Hits`}
          </b>
        </DmnJsPortal>
      </div>
    );
  }
}

export default themed(withErrorHandling(DecisionTable));

function getColor(theme, progress) {
  if (theme === 'light') {
    return `hsl(223, 100%, ${90 - progress * 30}%)`;
  } else {
    return `hsl(223, 100%, ${30 + progress * 30}%)`;
  }
}
