import React from 'react';
import classnames from 'classnames';

import {Select, BPMNDiagram} from 'components';

import {loadProcessDefinitions} from 'services';

import './ProcessDefinitionSelection.css';

export default class ProcessDefinitionSelection extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      availableDefinitions: [],
      loaded: false
    };
  }

  componentDidMount = async () => {
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

  canRenderDiagram = () => {
    return (
      this.props.renderDiagram &&
      this.props.processDefinitionKey &&
      this.props.processDefinitionVersion
    );
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
        className={classnames('ProcessDefinitionSelection', {
          'ProcessDefinitionSelection--large': this.canRenderDiagram()
        })}
      >
        <div className="ProcessDefinitionSelection__selects">
          <div className="ProcessDefinitionSelection__selects-item">
            <label
              htmlFor="ProcessDefinitionSelection__process-definition"
              className="ProcessDefinitionSelection__label"
            >
              Name
            </label>
            <Select
              className="ProcessDefinitionSelection__name-select"
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
          </div>
          <div className="ProcessDefinitionSelection__selects-item">
            <label
              htmlFor="ProcessDefinitionSelection__process-definition"
              className="ProcessDefinitionSelection__label"
            >
              Version
            </label>
            <Select
              className="ProcessDefinitionSelection__version-select"
              name="ProcessDefinitionSelection__version"
              value={this.getVersion()}
              onChange={this.changeVersion}
              disabled={!key}
            >
              {!key && <Select.Option defaultValue value="" />}
              {this.props.enableAllVersionSelection && (
                <Select.Option value="ALL" key="all">
                  all
                </Select.Option>
              )}
              {this.renderAllDefinitionVersions(key)}
            </Select>
          </div>
          {version === 'ALL' ? (
            <div className="ProcessDefinitionSelection__version-select__warning">
              Note: data from the older process versions can deviate, therefore the report data can
              be inconsistent
            </div>
          ) : (
            ''
          )}
        </div>
        {this.canRenderDiagram() && (
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
