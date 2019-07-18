/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import classnames from 'classnames';

import {InfoMessage, BPMNDiagram, LoadingIndicator, Popover, Typeahead, Labeled} from 'components';

import {loadDefinitions, capitalize} from 'services';

import TenantPopover from './TenantPopover';
import VersionPopover from './VersionPopover';

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

  hasDefinition = () => this.props.definitionKey;

  changeDefinition = ({key}) =>
    this.props.onChange(key, ['latest'], this.getDefinitionObject(key).tenants.map(({id}) => id));

  getDefinitionObject = key => this.state.availableDefinitions.find(def => def.key === key);
  canRenderDiagram = () => this.props.renderDiagram && this.props.xml;

  getAvailableTenants = key => {
    const definition = this.getDefinitionObject(key);
    if (definition) {
      return definition.tenants;
    }
    return [];
  };

  hasTenants = () => {
    const definition = this.getDefinitionObject(this.props.definitionKey);
    if (definition) {
      return definition.tenants.length >= 2;
    }
  };

  getSelectedTenants = () => this.props.tenants;

  changeTenants = tenantSelection => {
    this.props.onChange(this.props.definitionKey, this.props.versions, tenantSelection);
  };

  getAvailableVersions = key => {
    const definition = this.getDefinitionObject(key);
    if (definition) {
      return definition.versions;
    }
    return [];
  };

  getSelectedVersions = () => this.props.versions || [];

  changeVersions = versions => {
    this.props.onChange(this.props.definitionKey, versions, this.props.tenants);
  };

  createTitle = () => {
    const {definitionKey, versions, type} = this.props;

    if (definitionKey && versions) {
      const availableTenants = this.getAvailableTenants(definitionKey);
      const selectedTenants = this.getSelectedTenants();

      const definition = this.getDefinitionObject(definitionKey).name;

      let versionString = 'None';
      if (versions.length === 1 && versions[0] === 'all') {
        versionString = 'All';
      } else if (versions.length === 1 && versions[0] === 'latest') {
        versionString = 'Latest';
      } else if (versions.length === 1) {
        versionString = versions[0];
      } else if (versions.length > 1) {
        versionString = 'Multiple';
      }

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
        return `${definition} : ${versionString} : ${tenant}`;
      } else {
        return `${definition} : ${versionString}`;
      }
    } else {
      return `Select ${capitalize(type)}`;
    }
  };

  render() {
    const {loaded, availableDefinitions} = this.state;
    const noDefinitions = !availableDefinitions || availableDefinitions.length === 0;
    const selectedKey = this.props.definitionKey;
    const versions = this.getSelectedVersions();
    const displayVersionWarning = versions.length > 1 || versions[0] === 'all';

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
                  initialValue={this.getDefinitionObject(selectedKey)}
                  disabled={noDefinitions}
                  placeholder="Select..."
                  values={availableDefinitions}
                  onSelect={this.changeDefinition}
                  formatter={({name, key}) => name || key}
                  noValuesMessage="No defintions found"
                />
              </Labeled>
              <div className="version entry">
                <Labeled label="Version" />
                <VersionPopover
                  disabled={!this.hasDefinition()}
                  versions={this.getAvailableVersions(selectedKey)}
                  selected={this.getSelectedVersions()}
                  onChange={this.changeVersions}
                />
              </div>
              <div className="tenant entry">
                <Labeled label="Tenant" />
                <TenantPopover
                  tenants={this.getAvailableTenants(selectedKey)}
                  selected={this.getSelectedTenants()}
                  onChange={this.changeTenants}
                />
              </div>
            </div>
            {displayVersionWarning ? (
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
}
