import React from 'react';
import {Popover, ProcessDefinitionSelection, Button, Dropdown, Input, Labeled} from 'components';

import {Filter} from './filter';
import {
  extractProcessDefinitionName,
  getFlowNodeNames,
  reportConfig,
  formatters,
  getDataKeys,
  loadProcessDefinitionXml
} from 'services';

import {TargetValueComparison} from './targetValue';
import {ProcessPart} from './ProcessPart';

import {loadVariables, isChecked} from './service';

import {Configuration} from './Configuration';

import './ReportControlPanel.scss';

const {
  options: {view, groupBy, visualization},
  getLabelFor,
  isAllowed,
  update
} = reportConfig.process;
const groupByVariablePageSize = 5;

export default class ReportControlPanel extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      processDefinitionName: this.props.report.data.processDefinitionKey,
      variables: [],
      variableTypeaheadValue: '',
      variableStartIdx: 0,
      flowNodeNames: null
    };
  }

  componentDidMount() {
    this.loadProcessDefinitionName();
    this.loadVariables();
    this.loadFlowNodeNames();
  }

  loadFlowNodeNames = async () => {
    const {
      data: {processDefinitionKey, processDefinitionVersion}
    } = this.props.report;
    this.setState({
      flowNodeNames: await getFlowNodeNames(processDefinitionKey, processDefinitionVersion)
    });
  };

  loadProcessDefinitionName = async () => {
    const {
      configuration: {xml}
    } = this.props.report.data;
    if (xml) {
      const processDefinitionName = await extractProcessDefinitionName(xml);
      this.setState({
        processDefinitionName
      });
    }
  };

  loadVariables = async () => {
    const {processDefinitionKey, processDefinitionVersion} = this.props.report.data;
    if (processDefinitionKey && processDefinitionVersion) {
      this.setState({
        variables: await loadVariables(processDefinitionKey, processDefinitionVersion)
      });
    }
  };

  definitionConfig = () => {
    const {
      data: {processDefinitionKey, processDefinitionVersion}
    } = this.props.report;
    return {
      processDefinitionKey,
      processDefinitionVersion
    };
  };

  componentDidUpdate(prevProps) {
    const {data} = this.props.report;
    const {data: prevData} = prevProps.report;
    if (data.processDefinitionKey !== prevData.processDefinitionKey) {
      this.loadProcessDefinitionName();
    }

    if (
      data.processDefinitionKey !== prevData.processDefinitionKey ||
      data.processDefinitionVersion !== prevData.processDefinitionVersion
    ) {
      this.loadVariables();
      this.loadFlowNodeNames();
    }
  }

  createTitle = () => {
    const {processDefinitionKey, processDefinitionVersion} = this.props.report.data;
    const processDefintionName = this.state.processDefinitionName
      ? this.state.processDefinitionName
      : processDefinitionKey;
    if (processDefintionName && processDefinitionVersion) {
      return `${processDefintionName} : ${processDefinitionVersion}`;
    } else {
      return 'Select Process';
    }
  };

  changeDefinition = async (key, version) => {
    const {groupBy, filter} = this.props.report.data;

    const change = {
      processDefinitionKey: {$set: key},
      processDefinitionVersion: {$set: version},
      parameters: {$set: {}},
      filter: {$set: filter.filter(({type}) => type !== 'executedFlowNodes' && type !== 'variable')}
    };

    if (key && version) {
      change.configuration = {xml: {$set: await loadProcessDefinitionXml(key, version)}};
    }

    if (groupBy && groupBy.type === 'variable') {
      change.groupBy = {$set: null};
      change.visualization = {$set: null};
    }

    this.props.updateReport(change, true);
  };

  render() {
    const {data} = this.props.report;
    return (
      <div className="ReportControlPanel">
        <ul>
          <li className="select">
            <Labeled label="Process definition">
              <Popover className="processDefinitionPopover" title={this.createTitle()}>
                <ProcessDefinitionSelection
                  {...this.definitionConfig()}
                  xml={data.configuration.xml}
                  onChange={this.changeDefinition}
                  renderDiagram={true}
                  enableAllVersionSelection={true}
                />
              </Popover>
            </Labeled>
          </li>
          <li className="select">
            <Labeled label="View">{this.renderDropdown('view', view)}</Labeled>
          </li>
          <li className="select">
            <Labeled label="Group by">{this.renderDropdown('groupBy', groupBy)}</Labeled>
          </li>
          <li className="select">
            <Labeled label="Visualize as">
              {this.renderDropdown('visualization', visualization)}
            </Labeled>
          </li>
          <li className="filter">
            <Filter
              flowNodeNames={this.state.flowNodeNames}
              data={data.filter}
              onChange={this.props.updateReport}
              {...this.definitionConfig()}
              xml={data.configuration.xml}
              instanceCount={this.props.report && this.props.report.processInstanceCount}
            />
          </li>
          {this.shouldDisplayTargetValue() && (
            <li>
              <TargetValueComparison
                report={this.props.report}
                onChange={this.props.updateReport}
              />
            </li>
          )}
          <Configuration
            type={data.visualization}
            onChange={this.props.updateReport}
            report={this.props.report}
          />
          {this.shouldDisplayProcessPart() && (
            <li>
              <ProcessPart
                flowNodeNames={this.state.flowNodeNames}
                xml={data.configuration.xml}
                processPart={data.parameters.processPart}
                update={newPart =>
                  this.props.updateReport({parameters: {processPart: {$set: newPart}}}, true)
                }
              />
            </li>
          )}
        </ul>
      </div>
    );
  }

  shouldDisplayProcessPart = () => {
    const {view} = this.props.report.data;
    return view && view.entity === 'processInstance' && view.property === 'duration';
  };

  shouldDisplayTargetValue = () => {
    const {
      view,
      visualization,
      processDefinitionKey,
      processDefinitionVersion
    } = this.props.report.data;

    return (
      view &&
      view.entity === 'flowNode' &&
      view.property === 'duration' &&
      visualization === 'heat' &&
      processDefinitionKey &&
      processDefinitionVersion
    );
  };

  renderDropdown = (type, config) => {
    const {data} = this.props.report;
    const {
      processDefinitionKey,
      processDefinitionVersion,
      view,
      groupBy,
      configuration: {xml}
    } = data;
    let disabled = false;

    if (!processDefinitionKey || !processDefinitionVersion) {
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
        label={getLabelFor(config, data[type], xml) || 'Please Select...'}
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
    const disabled = type === 'groupBy' && !isAllowed(data.view, configData);
    const checked = isChecked(configData, data[type]);
    return (
      <Dropdown.Submenu
        label={label}
        key={key}
        disabled={disabled}
        checked={checked}
        onOpen={() => this.setState({variableStartIdx: 0, variableTypeaheadValue: ''})}
      >
        {type === 'groupBy' && key === 'variable'
          ? this.renderVariables()
          : configData[submenu].map((entry, idx) => {
              const subData = {...configData, [submenu]: entry.data};
              const checked = isChecked(subData, data[type]);
              return (
                <Dropdown.Option
                  key={idx}
                  checked={checked}
                  onClick={() =>
                    this.props.updateReport(
                      update(type, subData, this.props),
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
      disabled = !isAllowed(data.view, configData);
    } else if (type === 'visualization') {
      disabled = !isAllowed(data.view, data.groupBy, configData);
    }
    const checked = isChecked(configData, data[type]);
    return (
      <Dropdown.Option
        key={key}
        checked={checked}
        onClick={() =>
          this.props.updateReport(update(type, configData, this.props), type !== 'visualization')
        }
        disabled={disabled}
      >
        {label}
      </Dropdown.Option>
    );
  };

  renderVariables = () => {
    const {data} = this.props.report;
    const currentlySelected =
      data.groupBy && data.groupBy.type === 'variable' && data.groupBy.value;
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
      <div className="variablesGroupby" onClick={this.catchClick}>
        <div className="inputContainer">
          <Input
            value={this.state.variableTypeaheadValue}
            placeholder="Search for variables here"
            onKeyDown={evt => evt.stopPropagation()}
            onChange={evt =>
              this.setState({variableTypeaheadValue: evt.target.value, variableStartIdx: 0})
            }
          />
        </div>
        {currentlySelected && (
          <Dropdown.Option
            checked
            title={currentlySelected.name}
            onClick={evt => (evt.nativeEvent.isCloseEvent = true)}
          >
            {currentlySelected.name}
          </Dropdown.Option>
        )}
        {this.state.variableStartIdx > 0 && (
          <div className="inputContainer">
            <Button
              onClick={evt => {
                evt.preventDefault();
                this.setState({
                  variableStartIdx:
                    this.state.variableStartIdx -
                    Math.min(groupByVariablePageSize, this.state.variableStartIdx)
                });
              }}
            >
              Previous items
            </Button>
          </div>
        )}
        <div className="variableOptionsList">
          {filteredVars
            .slice(
              this.state.variableStartIdx,
              this.state.variableStartIdx + groupByVariablePageSize
            )
            .map((variable, idx) => (
              <Dropdown.Option
                key={idx}
                title={variable.name}
                onClick={evt => {
                  evt.nativeEvent.isCloseEvent = true;
                  this.props.updateReport(
                    update('groupBy', {type: 'variable', value: variable}, this.props)
                  );
                }}
              >
                {formatters.getHighlightedText(variable.name, this.state.variableTypeaheadValue)}
              </Dropdown.Option>
            ))}
        </div>
        {remaining > 0 && (
          <div className="inputContainer loadMore">
            <span>
              {remaining} more item{remaining > 1 && 's'}
            </span>
            <Button
              onClick={evt => {
                evt.preventDefault();
                this.setState({
                  variableStartIdx:
                    this.state.variableStartIdx + Math.min(groupByVariablePageSize, remaining)
                });
              }}
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
}
