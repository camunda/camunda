import React from 'react';
import {Popover, Dropdown, Labeled} from 'components';

import DecisionDefinitionSelection from './DecisionDefinitionSelection';

import {isChecked} from './service';
import {getDataKeys, decisionConfig} from 'services';

export default class DecisionControlPanel extends React.Component {
  createTitle = () => {
    const {decisionDefinitionKey, decisionDefinitionVersion} = this.props;
    if (decisionDefinitionKey && decisionDefinitionVersion) {
      return `${decisionDefinitionKey} : ${decisionDefinitionVersion}`;
    } else {
      return 'Select Decision';
    }
  };

  render() {
    return (
      <div className="DecisionControlPanel ReportControlPanel">
        <ul>
          <li className="select">
            <Labeled label="Decision definition">
              <Popover className="processDefinitionPopover" title={this.createTitle()}>
                <DecisionDefinitionSelection
                  decisionDefinitionKey={this.props.decisionDefinitionKey}
                  decisionDefinitionVersion={this.props.decisionDefinitionVersion}
                  onChange={this.props.updateReport}
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
    let disabled = false;

    if (!this.props.decisionDefinitionKey || !this.props.decisionDefinitionVersion) {
      disabled = true;
    }
    if (type === 'groupBy' && !this.props.view) {
      disabled = true;
    }
    if (type === 'visualization' && (!this.props.view || !this.props.groupBy)) {
      disabled = true;
    }
    return (
      <Dropdown
        label={decisionConfig.getLabelFor(config, this.props[type]) || 'Please Select...'}
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

  renderSubmenu = (submenu, type, data, label, key) => {
    const disabled = type === 'groupBy' && !decisionConfig.isAllowed(this.props.view, data);
    const checked = isChecked(data, this.props[type]);
    return (
      <Dropdown.Submenu label={label} key={key} disabled={disabled} checked={checked}>
        {data[submenu].map((entry, idx) => {
          const subData = {...data, [submenu]: entry.data};
          const checked = isChecked(subData, this.props[type]);
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

  renderNormalOption = (type, data, label, key) => {
    let disabled = false;
    if (type === 'groupBy') {
      disabled = !decisionConfig.isAllowed(this.props.view, data);
    } else if (type === 'visualization') {
      disabled = !decisionConfig.isAllowed(this.props.view, this.props.groupBy, data);
    }
    const checked = isChecked(data, this.props[type]);
    return (
      <Dropdown.Option
        key={key}
        checked={checked}
        onClick={() =>
          this.props.updateReport(
            decisionConfig.update(type, data, this.props),
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
