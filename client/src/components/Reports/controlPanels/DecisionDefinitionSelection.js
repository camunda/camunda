import React from 'react';
import {Select, LoadingIndicator, Labeled} from 'components';

import {loadDecisionDefinitions} from './service';

export default class ProcessDefinitionSelection extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      availableDefinitions: [],
      loaded: false
    };
  }

  componentDidMount = async () => {
    this.setState({
      availableDefinitions: await loadDecisionDefinitions(),
      loaded: true
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
    this.props.onChange(key, version);
  };

  changeVersion = async evt => {
    let version = evt.target.value;
    let key = this.props.decisionDefinitionKey;
    if (!version) {
      // reset to please select
      version = '';
    }
    this.props.onChange(key, version);
  };

  getLatestDefinition = key => {
    const selectedKeyGroup = this.findSelectedKeyGroup(key);
    return selectedKeyGroup.versions[0];
  };

  findSelectedKeyGroup = key => {
    return this.state.availableDefinitions.find(def => def.key === key);
  };

  getKey = () => {
    return this.props.decisionDefinitionKey ? this.props.decisionDefinitionKey : '';
  };

  getVersion = () => {
    return this.props.decisionDefinitionVersion ? this.props.decisionDefinitionVersion : '';
  };

  getNameForKey = key => {
    const definition = this.getLatestDefinition(key);
    return definition.name ? definition.name : key;
  };

  render() {
    const {loaded} = this.state;
    const key = this.props.decisionDefinitionKey;
    const version = this.props.decisionDefinitionVersion;

    if (!loaded) {
      return (
        <div className="ProcessDefinitionSelection">
          <LoadingIndicator />
        </div>
      );
    }

    return (
      <div className="ProcessDefinitionSelection">
        <div className="ProcessDefinitionSelection__selects">
          <Labeled className="ProcessDefinitionSelection__selects-item" label="Name">
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
          </Labeled>
          <Labeled className="ProcessDefinitionSelection__selects-item" label="Version">
            <Select
              className="ProcessDefinitionSelection__version-select"
              name="ProcessDefinitionSelection__version"
              value={this.getVersion()}
              onChange={this.changeVersion}
              disabled={!key}
            >
              {!key && <Select.Option defaultValue value="" />}
              <Select.Option value="ALL" key="all">
                all
              </Select.Option>
              {this.renderAllDefinitionVersions(key)}
            </Select>
          </Labeled>
          {version === 'ALL' ? (
            <div className="ProcessDefinitionSelection__version-select__warning">
              Note: data from the older decision versions can deviate, therefore the report data can
              be inconsistent
            </div>
          ) : (
            ''
          )}
        </div>
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
