/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import classnames from 'classnames';

import {BPMNDiagram, LoadingIndicator, Labeled, Dropdown, Typeahead} from 'components';

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

  changeKey = ({key}) => {
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

  createProcDefVersionTitle = () => {
    if (this.props.definitionVersion) {
      return this.getVersion().toLowerCase();
    } else {
      return '\u00A0';
    }
  };

  render() {
    const {loaded, availableDefinitions} = this.state;
    const noDefinitions = !availableDefinitions || availableDefinitions.length === 0;
    const selectedKey = this.props.definitionKey;
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
              <Typeahead
                className="name"
                initialValue={this.findSelectedKeyGroup(selectedKey)}
                disabled={noDefinitions}
                placeholder="Select..."
                values={availableDefinitions}
                onSelect={this.changeKey}
                formatter={selectedDefinition =>
                  selectedDefinition ? this.getNameForKey(selectedDefinition.key) : null
                }
              />
            </Labeled>
            <Labeled label="Version">
              <Dropdown
                label={this.createProcDefVersionTitle()}
                className="version"
                disabled={!selectedKey}
              >
                {this.props.enableAllVersionSelection && (
                  <Dropdown.Option key="0" onClick={() => this.changeVersion('ALL')}>
                    all
                  </Dropdown.Option>
                )}
                {this.renderAllVersions(selectedKey)}
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
