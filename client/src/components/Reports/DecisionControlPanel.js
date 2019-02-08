import React from 'react';
import {Popover, Dropdown, Labeled} from 'components';

import DecisionDefinitionSelection from './DecisionDefinitionSelection';

import {isChecked, loadDecisionDefinitionXml} from './service';
import {getDataKeys, decisionConfig} from 'services';

export default class DecisionControlPanel extends React.Component {
  createTitle = () => {
    const {decisionDefinitionKey, decisionDefinitionVersion} = this.props.report.data;
    if (decisionDefinitionKey && decisionDefinitionVersion) {
      return `${decisionDefinitionKey} : ${decisionDefinitionVersion}`;
    } else {
      return 'Select Decision';
    }
  };

  changeDefinition = async (key, version) => {
    const change = {
      decisionDefinitionKey: {$set: key},
      decisionDefinitionVersion: {$set: version}
    };

    if (key && version) {
      change.configuration = {xml: {$set: await loadDecisionDefinitionXml(key, version)}};
    }

    this.props.updateReport(change, true);
  };

  render() {
    const {
      data: {decisionDefinitionKey, decisionDefinitionVersion}
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
        </ul>
      </div>
    );
  }

  renderDropdown = (type, config) => {
    const {data} = this.props.report;
    const {decisionDefinitionKey, decisionDefinitionVersion, view, groupBy} = data;
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
        label={decisionConfig.getLabelFor(config, data[type]) || 'Please Select...'}
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
    return (
      <Dropdown.Submenu label={label} key={key} disabled={disabled} checked={checked}>
        {configData[submenu].map((entry, idx) => {
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
