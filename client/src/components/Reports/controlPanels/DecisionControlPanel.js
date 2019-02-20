import React from 'react';
import {Popover, Dropdown, Labeled} from 'components';

import DecisionDefinitionSelection from './DecisionDefinitionSelection';
import {Configuration} from './Configuration';

import {DecisionFilter} from './filter';

import {isChecked} from './service';
import {
  getDataKeys,
  reportConfig,
  loadDecisionDefinitionXml,
  extractDefinitionName
} from 'services';
const {decision: decisionConfig} = reportConfig;

export default class DecisionControlPanel extends React.Component {
  state = {};

  static getDerivedStateFromProps({
    report: {
      data: {
        configuration: {xml},
        decisionDefinitionKey
      }
    }
  }) {
    const definitions = new DOMParser()
      .parseFromString(xml, 'text/xml')
      .querySelector(`decision[id="${decisionDefinitionKey}"]`);

    if (definitions) {
      return {
        variables: {
          inputVariable: [...definitions.querySelectorAll('input')].map(node => ({
            id: node.getAttribute('id'),
            name: node.getAttribute('label'),
            type: node.querySelector('inputExpression').getAttribute('typeRef')
          })),
          outputVariable: [...definitions.querySelectorAll('output')].map(node => ({
            id: node.getAttribute('id'),
            name: node.getAttribute('label'),
            type: node.getAttribute('typeRef')
          }))
        }
      };
    }
    return null;
  }

  createTitle = () => {
    const {
      decisionDefinitionKey,
      decisionDefinitionVersion,
      configuration: {xml}
    } = this.props.report.data;
    if (xml) {
      return `${extractDefinitionName(decisionDefinitionKey, xml)} : ${decisionDefinitionVersion}`;
    } else {
      return 'Select Decision';
    }
  };

  changeDefinition = async (key, version) => {
    const {groupBy, filter} = this.props.report.data;

    const change = {
      decisionDefinitionKey: {$set: key},
      decisionDefinitionVersion: {$set: version},
      filter: {
        $set: filter.filter(({type}) => type !== 'inputVariable' && type !== 'outputVariable')
      }
    };

    if (key && version) {
      change.configuration = {xml: {$set: await loadDecisionDefinitionXml(key, version)}};
    } else {
      change.configuration = {xml: {$set: null}};
    }

    if (groupBy && (groupBy.type === 'inputVariable' || groupBy.type === 'outputVariable')) {
      change.groupBy = {$set: null};
      change.visualization = {$set: null};
    }

    this.props.updateReport(change, true);
  };

  render() {
    const {
      data: {decisionDefinitionKey, decisionDefinitionVersion, filter, visualization},
      decisionInstanceCount
    } = this.props.report;
    return (
      <div className="DecisionControlPanel ReportControlPanel">
        <ul>
          <li className="select">
            <Labeled label="Decision definition">
              <Popover className="processDefinitionPopover" title={this.createTitle()}>
                <DecisionDefinitionSelection
                  decisionDefinitionKey={decisionDefinitionKey}
                  decisionDefinitionVersion={decisionDefinitionVersion}
                  onChange={this.changeDefinition}
                />
              </Popover>
            </Labeled>
          </li>
          <li className="select">
            <Labeled label="View">
              {this.renderDropdown('view', decisionConfig.options.view)}
            </Labeled>
          </li>
          <li className="select">
            <Labeled label="Group by">
              {this.renderDropdown('groupBy', decisionConfig.options.groupBy)}
            </Labeled>
          </li>
          <li className="select">
            <Labeled label="Visualize as">
              {this.renderDropdown('visualization', decisionConfig.options.visualization)}
            </Labeled>
          </li>
          <li className="filter">
            <DecisionFilter
              data={filter}
              onChange={this.props.updateReport}
              instanceCount={decisionInstanceCount}
              decisionDefinitionKey={decisionDefinitionKey}
              decisionDefinitionVersion={decisionDefinitionVersion}
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

  renderDropdown = (type, config) => {
    const {data} = this.props.report;
    const {
      decisionDefinitionKey,
      decisionDefinitionVersion,
      view,
      groupBy,
      configuration: {xml}
    } = data;
    let disabled = false;

    if (!decisionDefinitionKey || !decisionDefinitionVersion) {
      disabled = true;
    }
    if (type === 'groupBy' && !view) {
      disabled = true;
    }
    if (type === 'visualization' && (!view || !groupBy)) {
      disabled = true;
    }
    return (
      <Dropdown
        label={decisionConfig.getLabelFor(config, data[type], xml) || 'Please Select...'}
        className="configDropdown"
        disabled={disabled}
      >
        {Object.keys(config).map(key => {
          const {label, data} = config[key];

          const submenu = getDataKeys(data).find(key => Array.isArray(data[key]));
          if (submenu) {
            return this.renderSubmenu(submenu, type, data, label, key);
          } else {
            return this.renderNormalOption(type, data, label, key);
          }
        })}
      </Dropdown>
    );
  };

  renderSubmenu = (submenu, type, configData, label, key) => {
    const {data} = this.props.report;
    const disabled = type === 'groupBy' && !decisionConfig.isAllowed(data.view, configData);
    const checked = isChecked(configData, data[type]);

    let options = configData[submenu];

    if (data.decisionDefinitionKey && type === 'groupBy' && key.includes('Variable')) {
      options = this.state.variables[key].map(({id, name}) => ({
        data: {id},
        label: name
      }));
    }

    return (
      <Dropdown.Submenu label={label} key={key} disabled={disabled} checked={checked}>
        {options.map((entry, idx) => {
          const subData = {...configData, [submenu]: entry.data};
          const checked = isChecked(subData, data[type]);
          return (
            <Dropdown.Option
              key={idx}
              checked={checked}
              onClick={() =>
                this.props.updateReport(
                  decisionConfig.update(type, subData, this.props),
                  type !== 'visualization'
                )
              }
            >
              {entry.label}
            </Dropdown.Option>
          );
        })}
      </Dropdown.Submenu>
    );
  };

  renderNormalOption = (type, configData, label, key) => {
    const {data} = this.props.report;
    let disabled = false;
    if (type === 'groupBy') {
      disabled = !decisionConfig.isAllowed(data.view, configData);
    } else if (type === 'visualization') {
      disabled = !decisionConfig.isAllowed(data.view, data.groupBy, configData);
    }
    const checked = isChecked(configData, data[type]);
    return (
      <Dropdown.Option
        key={key}
        checked={checked}
        onClick={() =>
          this.props.updateReport(
            decisionConfig.update(type, configData, this.props),
            type !== 'visualization'
          )
        }
        disabled={disabled}
      >
        {label}
      </Dropdown.Option>
    );
  };
}
