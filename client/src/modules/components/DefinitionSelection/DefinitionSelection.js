/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import classnames from 'classnames';

import {BPMNDiagram, LoadingIndicator, Labeled, Dropdown} from 'components';

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

  changeKey = key => {
    let version;
    if (!key) {
      version = '';
    } else {
      const selectedDefinition = this.getLatestDefinition(key);
      version = selectedDefinition.version;
    }
    this.props.onChange(key, version);
  };

  changeVersion = version => {
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

  getVersion = () => (this.props.definitionVersion ? this.props.definitionVersion : '');

  getNameForKey = key => {
    const definition = this.getLatestDefinition(key);
    return definition.name ? definition.name : key;
  };

  canRenderDiagram = () =>
    this.props.renderDiagram && this.props.definitionKey && this.props.definitionVersion;

  createProcDefKeyTitle = () => {
    if (this.props.definitionKey) {
      return this.getNameForKey(this.props.definitionKey);
    } else {
      return 'Select...';
    }
  };

  createProcDefVersionTitle = () => {
    if (this.props.definitionVersion) {
      return this.getVersion().toLowerCase();
    } else {
      return '\u00A0';
    }
  };

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
        <div className="selectionPanel">
          <div className="dropdowns">
            <Labeled label="Name">
              <Dropdown label={this.createProcDefKeyTitle()} className="name">
                {this.state.availableDefinitions.map(({key}) => {
                  return (
                    <Dropdown.Option key={key} onClick={() => this.changeKey(key)}>
                      {this.getNameForKey(key)}
                    </Dropdown.Option>
                  );
                })}
              </Dropdown>
            </Labeled>
            <Labeled label="Version">
              <Dropdown
                label={this.createProcDefVersionTitle()}
                className="version"
                disabled={!key}
              >
                {this.props.enableAllVersionSelection && (
                  <Dropdown.Option key="0" onClick={() => this.changeVersion('ALL')}>
                    all
                  </Dropdown.Option>
                )}
                {this.renderAllVersions(key)}
              </Dropdown>
            </Labeled>
          </div>
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
      <Dropdown.Option key={version} onClick={() => this.changeVersion(version)}>
        {version}
      </Dropdown.Option>
    ));
}
