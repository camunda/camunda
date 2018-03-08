import React from 'react';

import {Select, BPMNDiagram} from 'components';

import {loadProcessDefinitions} from './service';

import './ProcessDefinitionSelection.css';

export default class ControlPanel extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      availableDefinitions: [],
      loaded: false
    };

    this.loadAvailableDefinitions();
  }

  loadAvailableDefinitions = async () => {
    const availableDefinitions = await loadProcessDefinitions();

    this.setState({
      availableDefinitions,
      loaded: true
    });
  };

  propagateChange = (key, version) => {
    this.props.onChange({
      processDefinitionKey: key,
      processDefinitionVersion: version
    });
  };

  changeKey = async evt => {
    let key = evt.target.value;
    let version;
    if (!key) {
      version = '';
    } else {
      const selectedDefinition = this.getLatestDefinition(key);
      version = selectedDefinition.version;
    }
    this.propagateChange(key, version);
  };

  changeVersion = async evt => {
    let version = evt.target.value;
    let key = this.props.processDefinitionKey;
    if (!version) {
      // reset to please select
      version = '';
    }
    this.propagateChange(key, version);
  };

  getLatestDefinition = key => {
    const selectedKeyGroup = this.findSelectedKeyGroup(key);
    return selectedKeyGroup.versions[0];
  };

  findSelectedKeyGroup = key => {
    return this.state.availableDefinitions.find(def => def.key === key);
  };

  getKey = () => {
    return this.props.processDefinitionKey ? this.props.processDefinitionKey : '';
  };

  getVersion = () => {
    return this.props.processDefinitionVersion ? this.props.processDefinitionVersion : '';
  };

  getNameForKey = key => {
    const definition = this.getLatestDefinition(key);
    return definition.name ? definition.name : key;
  };

  render() {
    const {loaded} = this.state;
    const key = this.props.processDefinitionKey;
    const version = this.props.processDefinitionVersion;

    if (!loaded) {
      return <div className="ProcessDefinitionSelection__loading-indicator">loading...</div>;
    }

    return (
      <div
        className={
          'ProcessDefinitionSelection' +
          (this.props.renderDiagram ? ' ProcessDefinitionSelection--large' : '')
        }
      >
        <ul className="ProcessDefinitionSelection__list">
          <li className="ProcessDefinitionSelection__list-item">
            <label
              htmlFor="ProcessDefinitionSelection__process-definition"
              className="ProcessDefinitionSelection__label"
            >
              Name
            </label>
            <Select
              className="ProcessDefinitionSelection__select"
              name="ProcessDefinitionSelection__key"
              value={this.getKey()}
              onChange={this.changeKey}
            >
              {
                <Select.Option defaultValue value="">
                  Please select...
                </Select.Option>
              }
              {this.state.availableDefinitions.map(({key}) => {
                return (
                  <Select.Option value={key} key={key}>
                    {this.getNameForKey(key)}
                  </Select.Option>
                );
              })}
            </Select>
          </li>
          <li className="ProcessDefinitionSelection__list-item">
            <label
              htmlFor="ProcessDefinitionSelection__process-definition"
              className="ProcessDefinitionSelection__label"
            >
              Version
            </label>
            <Select
              className="ProcessDefinitionSelection__select"
              name="ProcessDefinitionSelection__version"
              value={this.getVersion()}
              onChange={this.changeVersion}
              disabled={!key}
            >
              <Select.Option defaultValue value="">
                Please select...
              </Select.Option>
              {this.props.enableAllVersionSelection && (
                <Select.Option value="ALL" key="all">
                  all
                </Select.Option>
              )}
              {this.renderAllDefinitionVersions(key)}
            </Select>
          </li>
        </ul>
        {this.props.renderDiagram &&
          key &&
          version && (
            <div className={'ProcessDefinitionSelection__diagram'}>
              <BPMNDiagram xml={this.props.xml} disableNavigation />
            </div>
          )}
      </div>
    );
  }

  renderAllDefinitionVersions = key => {
    return (
      key &&
      this.findSelectedKeyGroup(key).versions.map(({version}) => (
        <Select.Option value={version} key={version}>
          {version}
        </Select.Option>
      ))
    );
  };
}
