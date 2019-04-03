/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import classnames from 'classnames';

import {Select, BPMNDiagram, LoadingIndicator, Labeled} from 'components';

import {loadDefinitions} from 'services';

import './DefinitionSelection.scss';

export default class DefinitionSelection extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      availableDefinitions: [],
      loaded: false
    };
  }

  componentDidMount = async () => {
    const availableDefinitions = await loadDefinitions(this.props.type);

    this.setState({
      availableDefinitions,
      loaded: true
    });
  };

  changeKey = evt => {
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

  changeVersion = evt => {
    let version = evt.target.value;
    if (!version) {
      // reset to please select
      version = '';
    }
    this.props.onChange(this.props.definitionKey, version);
  };

  getLatestDefinition = key => {
    const selectedKeyGroup = this.findSelectedKeyGroup(key);
    return selectedKeyGroup.versions[0];
  };

  findSelectedKeyGroup = key => this.state.availableDefinitions.find(def => def.key === key);

  getKey = () => (this.props.definitionKey ? this.props.definitionKey : '');
  getVersion = () => (this.props.definitionVersion ? this.props.definitionVersion : '');

  getNameForKey = key => {
    const definition = this.getLatestDefinition(key);
    return definition.name ? definition.name : key;
  };

  canRenderDiagram = () =>
    this.props.renderDiagram && this.props.definitionKey && this.props.definitionVersion;

  render() {
    const {loaded} = this.state;
    const key = this.props.definitionKey;
    const version = this.props.definitionVersion;

    if (!loaded) {
      return (
        <div className="DefinitionSelection">
          <LoadingIndicator />
        </div>
      );
    }

    return (
      <div
        className={classnames('DefinitionSelection', {
          large: this.canRenderDiagram()
        })}
      >
        <div className="selects">
          <Labeled label="Name">
            <Select
              className="name"
              name="DefinitionSelection__key"
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
          <Labeled label="Version">
            <Select
              className="version"
              name="DefinitionSelection__version"
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
              {this.renderAllVersions(key)}
            </Select>
          </Labeled>
          {version === 'ALL' ? (
            <div className="warning">
              Note: data from the older versions can deviate, therefore the report data can be
              inconsistent
            </div>
          ) : (
            ''
          )}
        </div>
        {this.canRenderDiagram() && (
          <div className="diagram">
            <BPMNDiagram xml={this.props.xml} disableNavigation />
          </div>
        )}
      </div>
    );
  }

  renderAllVersions = key =>
    key &&
    this.findSelectedKeyGroup(key).versions.map(({version}) => (
      <Select.Option value={version} key={version}>
        {version}
      </Select.Option>
    ));
}
