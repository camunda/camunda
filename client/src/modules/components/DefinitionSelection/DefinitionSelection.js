/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import classnames from 'classnames';

import {
  InfoMessage,
  BPMNDiagram,
  LoadingIndicator,
  Popover,
  Select,
  Typeahead,
  Labeled
} from 'components';

import {loadDefinitions, capitalize} from 'services';

import TenantPopover from './TenantPopover';

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
    this.setState({
      availableDefinitions: (await loadDefinitions(this.props.type)).map(entry => ({
        ...entry,
        id: entry.key
      })),
      loaded: true
    });
  };

  changeKey = payload => {
    const key = payload.key;
    const selectedDefinition = this.getLatestDefinition(key);
    const version = selectedDefinition.version;
    const tenants = selectedDefinition.tenants.map(({id}) => id);

    this.props.onChange(key, version, tenants);
  };

  changeVersion = (version, tenants) => {
    this.props.onChange(this.props.definitionKey, version, tenants.map(({id}) => id));
  };

  getLatestDefinition = key => {
    const selectedKeyGroup = this.findSelectedKeyGroup(key);
    return selectedKeyGroup.versions[1];
  };

  findSelectedKeyGroup = key => this.state.availableDefinitions.find(def => def.key === key);

  getVersion = () => (this.props.definitionVersion ? this.props.definitionVersion : '');

  getNameForKey = key => {
    const definition = this.getLatestDefinition(key);
    return definition.name ? definition.name : key;
  };

  canRenderDiagram = () =>
    this.props.renderDiagram && this.props.definitionKey && this.props.definitionVersion;

  getAvailableTenants = (definitionKey, definitionVersion) => {
    const definition = this.findSelectedKeyGroup(definitionKey);
    if (definition) {
      const version = definition.versions.find(({version}) => version === definitionVersion);
      if (version) {
        return version.tenants;
      }
    }

    return [];
  };

  hasTenants = () => {
    const definition = this.findSelectedKeyGroup(this.props.definitionKey);
    if (definition) {
      return definition.versions.find(({tenants}) => tenants.length >= 2);
    }
  };

  getSelectedTenants = () => this.props.tenants;

  changeTenants = tenantSelection => {
    this.props.onChange(this.props.definitionKey, this.props.definitionVersion, tenantSelection);
  };

  createTitle = () => {
    const {definitionKey, definitionVersion, type} = this.props;

    if (definitionKey && definitionVersion) {
      const availableTenants = this.getAvailableTenants(definitionKey, definitionVersion);
      const selectedTenants = this.getSelectedTenants();

      const definition = this.findSelectedKeyGroup(definitionKey).name;
      const version = definitionVersion.toLowerCase();
      let tenant = 'Multiple';
      if (selectedTenants.length === 0) {
        tenant = '-';
      } else if (availableTenants.length === 1) {
        tenant = null;
      } else if (selectedTenants.length === availableTenants.length) {
        tenant = 'All';
      } else if (selectedTenants.length === 1) {
        tenant = availableTenants.find(({id}) => id === selectedTenants[0]).name;
      }

      if (tenant) {
        return `${definition} : ${version} : ${tenant}`;
      } else {
        return `${definition} : ${version}`;
      }
    } else {
      return `Select ${capitalize(type)}`;
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
      <Popover className="DefinitionSelection" title={this.createTitle()}>
        <div
          className={classnames('container', {
            large: this.canRenderDiagram(),
            withTenants: this.hasTenants()
          })}
        >
          <div className="selectionPanel">
            <div className="dropdowns">
              <Labeled className="entry" label="Name">
                <Typeahead
                  className="name"
                  initialValue={this.findSelectedKeyGroup(selectedKey)}
                  disabled={noDefinitions}
                  placeholder="Select..."
                  values={availableDefinitions}
                  onSelect={this.changeKey}
                  formatter={({name, key}) => name || key}
                  noValuesMessage="No defintions found"
                />
              </Labeled>
              <Labeled label="Version" className="entry">
                <Select
                  className="version"
                  disabled={!selectedKey}
                  onChange={version =>
                    this.changeVersion(version, this.getAvailableTenants(selectedKey, version))
                  }
                  value={this.getVersion()}
                >
                  {this.renderAllVersions(selectedKey)}
                </Select>
              </Labeled>
              <div className="tenant entry">
                <Labeled label="Tenant" />
                <TenantPopover
                  tenants={this.getAvailableTenants(selectedKey, version)}
                  selected={this.getSelectedTenants()}
                  onChange={this.changeTenants}
                />
              </div>
            </div>
            {version === 'ALL' ? (
              <InfoMessage>
                Note: data from the older versions can deviate, therefore the report data can be
                inconsistent
              </InfoMessage>
            ) : (
              ''
            )}
          </div>
          {this.canRenderDiagram() && (
            <div className="diagram">
              <hr />
              <BPMNDiagram xml={this.props.xml} disableNavigation />
            </div>
          )}
        </div>
      </Popover>
    );
  }

  renderAllVersions = key =>
    key &&
    this.findSelectedKeyGroup(key)
      .versions.filter(({version}) => this.props.enableAllVersionSelection || version !== 'ALL')
      .map(({version}) => (
        <Select.Option key={version} value={version}>
          {version.toLowerCase()}
        </Select.Option>
      ));
}
