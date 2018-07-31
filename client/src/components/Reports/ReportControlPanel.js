import React from 'react';
import {Popover, ProcessDefinitionSelection, Button, Dropdown, Input} from 'components';

import {Filter} from './filter';
import {extractProcessDefinitionName, reportConfig} from 'services';

import {TargetValueComparison} from './targetValue';

import {loadVariables} from './service';

import './ReportControlPanel.css';

const {view, groupBy, visualization, getLabelFor, isAllowed, getNext} = reportConfig;
const groupByVariablePageSize = 5;

const getDataKeys = data => Object.keys(typeof data === 'object' ? data : {...data});

export default class ReportControlPanel extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      processDefinitionName: this.props.processDefinitionKey,
      variables: [],
      variableTypeaheadValue: '',
      variableStartIdx: 0
    };
  }

  componentDidMount() {
    this.loadProcessDefinitionName();
    this.loadVariables();
  }

  loadProcessDefinitionName = async () => {
    const {xml} = this.props.configuration;
    if (xml) {
      const processDefinitionName = await extractProcessDefinitionName(xml);
      this.setState({
        processDefinitionName
      });
    }
  };

  loadVariables = async () => {
    const {processDefinitionKey, processDefinitionVersion} = this.props;
    if (processDefinitionKey && processDefinitionVersion) {
      this.setState({
        variables: await loadVariables(processDefinitionKey, processDefinitionVersion)
      });
    }
  };

  definitionConfig = () => {
    return {
      processDefinitionKey: this.props.processDefinitionKey,
      processDefinitionVersion: this.props.processDefinitionVersion
    };
  };

  componentDidUpdate(prevProps) {
    if (this.props.processDefinitionKey !== prevProps.processDefinitionKey) {
      this.loadProcessDefinitionName();
    }

    if (
      this.props.processDefinitionKey !== prevProps.processDefinitionKey ||
      this.props.processDefinitionVersion !== prevProps.processDefinitionVersion
    ) {
      this.loadVariables();
    }
  }

  createTitle = () => {
    const {processDefinitionKey, processDefinitionVersion} = this.props;
    const processDefintionName = this.state.processDefinitionName
      ? this.state.processDefinitionName
      : processDefinitionKey;
    if (processDefintionName && processDefinitionVersion) {
      return `${processDefintionName} : ${processDefinitionVersion}`;
    } else {
      return 'Select Process Definition';
    }
  };

  render() {
    return (
      <div className="ReportControlPanel">
        <ul className="ReportControlPanel__list">
          <li className="ReportControlPanel__item ReportControlPanel__item--select">
            <label
              htmlFor="ReportControlPanel__process-definition"
              className="ReportControlPanel__label"
            >
              Process definition
            </label>
            <Popover className="ReportControlPanel__popover" title={this.createTitle()}>
              <ProcessDefinitionSelection
                {...this.definitionConfig()}
                xml={this.props.configuration.xml}
                onChange={this.props.updateReport}
                renderDiagram={true}
                enableAllVersionSelection={true}
              />
            </Popover>
          </li>
          <li className="ReportControlPanel__item ReportControlPanel__item--select">
            <label htmlFor="ReportControlPanel__view" className="ReportControlPanel__label">
              View
            </label>
            {this.renderDropdown('view', view)}
          </li>
          <li className="ReportControlPanel__item ReportControlPanel__item--select">
            <label htmlFor="ReportControlPanel__group-by" className="ReportControlPanel__label">
              Group by
            </label>
            {this.renderDropdown('groupBy', groupBy)}
          </li>
          <li className="ReportControlPanel__item ReportControlPanel__item--select">
            <label htmlFor="ReportControlPanel__visualize-as" className="ReportControlPanel__label">
              Visualize as
            </label>
            {this.renderDropdown('visualization', visualization)}
          </li>
          <li className="ReportControlPanel__item ReportControlPanel__item--filter">
            <Filter
              data={this.props.filter}
              onChange={this.props.updateReport}
              {...this.definitionConfig()}
              xml={this.props.configuration.xml}
            />
          </li>
          <li className="ReportControlPanel__item">
            <TargetValueComparison
              reportResult={this.props.reportResult}
              configuration={this.props.configuration}
              onChange={this.props.updateReport}
            />
          </li>
          {this.props.visualization === 'heat' && (
            <li className="ReportControlPanel__item">
              <Button
                active={this.props.configuration.alwaysShowTooltips}
                onClick={this.toggleAllTooltips}
              >
                Always show tooltips
              </Button>
            </li>
          )}
        </ul>
      </div>
    );
  }

  toggleAllTooltips = () => {
    this.props.updateReport({
      configuration: {
        ...this.props.configuration,
        alwaysShowTooltips: !this.props.configuration.alwaysShowTooltips
      }
    });
  };

  renderDropdown = (type, config) => {
    let disabled = false;
    if (type === 'groupBy' && !this.props.view) {
      disabled = true;
    }
    if (type === 'visualization' && (!this.props.view || !this.props.groupBy)) {
      disabled = true;
    }
    return (
      <Dropdown
        label={getLabelFor(config, this.props[type]) || 'Please Select...'}
        className="ReportControlPanel__dropdown"
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
    const disabled = type === 'groupBy' && !isAllowed(this.props.view, data);
    const checked = isChecked(data, this.props[type]);
    return (
      <Dropdown.Submenu
        label={label}
        key={key}
        disabled={disabled}
        checked={checked}
        onClick={() => this.setState({variableStartIdx: 0, variableTypeaheadValue: ''})}
      >
        {type === 'groupBy' && key === 'variable'
          ? this.renderVariables()
          : data[submenu].map((entry, idx) => {
              const subData = {...data, [submenu]: entry.data};
              const checked = isChecked(subData, this.props[type]);
              return (
                <Dropdown.Option
                  key={idx}
                  checked={checked}
                  onClick={() => this.update(type, subData)}
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
      disabled = !isAllowed(this.props.view, data);
    } else if (type === 'visualization') {
      disabled = !isAllowed(this.props.view, this.props.groupBy, data);
    }
    const checked = isChecked(data, this.props[type]);
    return (
      <Dropdown.Option
        key={key}
        checked={checked}
        onClick={() => this.update(type, data)}
        disabled={disabled}
      >
        {label}
      </Dropdown.Option>
    );
  };

  renderVariables = () => {
    const currentlySelected =
      this.props.groupBy && this.props.groupBy.type === 'variable' && this.props.groupBy.value;
    const filteredVars = this.state.variables.filter(
      ({name, type}) =>
        name.toLowerCase().includes(this.state.variableTypeaheadValue.toLowerCase()) &&
        (!currentlySelected || name !== currentlySelected.name || type !== currentlySelected.type)
    );
    const remaining = Math.max(
      filteredVars.length - this.state.variableStartIdx - groupByVariablePageSize,
      0
    );
    return (
      <div className="ReportControlPanel__variablesDropdown" onClick={this.catchClick}>
        <div className="ReportControlPanel__variableInputContainer">
          <Input
            value={this.state.variableTypeaheadValue}
            onKeyDown={evt => evt.stopPropagation()}
            onChange={evt =>
              this.setState({variableTypeaheadValue: evt.target.value, variableStartIdx: 0})
            }
          />
        </div>
        {currentlySelected && (
          <Dropdown.Option checked onClick={evt => (evt.nativeEvent.isCloseEvent = true)}>
            {currentlySelected.name}
          </Dropdown.Option>
        )}
        {this.state.variableStartIdx > 0 && (
          <div className="ReportControlPanel__variableInputContainer">
            <Button
              onClick={() =>
                this.setState({
                  variableStartIdx:
                    this.state.variableStartIdx -
                    Math.min(groupByVariablePageSize, this.state.variableStartIdx)
                })
              }
            >
              Previous items
            </Button>
          </div>
        )}
        <div className="ReportControlPanel__variableOptionsList">
          {filteredVars
            .slice(
              this.state.variableStartIdx,
              this.state.variableStartIdx + groupByVariablePageSize
            )
            .map((variable, idx) => (
              <Dropdown.Option
                key={idx}
                onClick={evt => {
                  evt.nativeEvent.isCloseEvent = true;
                  this.update('groupBy', {type: 'variable', value: {...variable}});
                }}
              >
                {variable.name}
              </Dropdown.Option>
            ))}
        </div>
        {remaining > 0 && (
          <div className="ReportControlPanel__variableInputContainer ReportControlPanel__loadMoreContainer">
            <span className="ReportControlPanel__moreItemsLabel">
              {remaining} more item{remaining > 1 && 's'}
            </span>
            <Button
              onClick={() =>
                this.setState({
                  variableStartIdx:
                    this.state.variableStartIdx + Math.min(groupByVariablePageSize, remaining)
                })
              }
            >
              Load More
            </Button>
          </div>
        )}
      </div>
    );
  };

  catchClick = evt => {
    if (!evt.nativeEvent.isCloseEvent) {
      evt.stopPropagation();
    }
  };

  update = (type, data) => {
    const update = {
      [type]: data,
      configuration: {
        ...this.props.configuration,
        targetValue: {active: false}
      }
    };

    const config = {
      view: this.props.view,
      groupBy: this.props.groupBy,
      visualization: this.props.visualization,
      ...update
    };

    const nextGroup = getNext(config.view);
    if (nextGroup) {
      config.groupBy = nextGroup;
      update.groupBy = nextGroup;
    }

    const nextVis = getNext(config.view, config.groupBy);
    if (nextVis) {
      config.visualization = nextVis;
      update.visualization = nextVis;
    }
    if (!isAllowed(config.view, config.groupBy)) {
      update.groupBy = null;
      update.visualization = null;
    } else if (!isAllowed(config.view, config.groupBy, config.visualization)) {
      update.visualization = null;
    }
    this.props.updateReport(update);
  };
}

function isChecked(data, current) {
  return (
    current &&
    getDataKeys(data).every(
      prop =>
        JSON.stringify(current[prop]) === JSON.stringify(data[prop]) || Array.isArray(data[prop])
    )
  );
}
