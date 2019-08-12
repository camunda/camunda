/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import classnames from 'classnames';

import {InfoMessage, BPMNDiagram, LoadingIndicator, Popover, Typeahead, Labeled} from 'components';

import {loadDefinitions} from 'services';

import TenantPopover from './TenantPopover';
import VersionPopover from './VersionPopover';

import './DefinitionSelection.scss';
import {t} from 'translation';

export default class DefinitionSelection extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      availableDefinitions: [],
      selectedSpecificVersions: this.isSpecificVersion(props.versions) ? props.versions : [],
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

  changeDefinition = ({key}) => {
    const definitionObject = this.getDefinitionObject(key);
    const latestVersion = [definitionObject.versions[0].version];

    this.setState({selectedSpecificVersions: latestVersion});
    this.props.onChange(key, latestVersion, definitionObject.tenants.map(({id}) => id));
  };

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
    if (this.isSpecificVersion(versions)) {
      this.setState({selectedSpecificVersions: versions});
    }
    this.props.onChange(this.props.definitionKey, versions, this.props.tenants);
  };

  isSpecificVersion = versions => versions && versions[0] !== 'latest' && versions[0] !== 'all';

  createTitle = () => {
    const {definitionKey, versions, type} = this.props;

    if (definitionKey && versions) {
      const availableTenants = this.getAvailableTenants(definitionKey);
      const selectedTenants = this.getSelectedTenants();

      const definition = this.getDefinitionObject(definitionKey).name;

      let versionString = t('common.none');
      if (versions.length === 1 && versions[0] === 'all') {
        versionString = t('common.all');
      } else if (versions.length === 1 && versions[0] === 'latest') {
        versionString = t('common.definitionSelection.latest');
      } else if (versions.length === 1) {
        versionString = versions[0];
      } else if (versions.length > 1) {
        versionString = t('common.definitionSelection.multiple');
      }

      let tenant = t('common.definitionSelection.multiple');
      if (selectedTenants.length === 0) {
        tenant = '-';
      } else if (availableTenants.length === 1) {
        tenant = null;
      } else if (selectedTenants.length === availableTenants.length) {
        tenant = t('common.all');
      } else if (selectedTenants.length === 1) {
        tenant = availableTenants.find(({id}) => id === selectedTenants[0]).name;
      }

      if (tenant) {
        return `${definition} : ${versionString} : ${tenant}`;
      } else {
        return `${definition} : ${versionString}`;
      }
    } else {
      return t(`common.definitionSelection.select.${type}`);
    }
  };

  render() {
    const {loaded, availableDefinitions, selectedSpecificVersions} = this.state;
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
              <Labeled className="entry" label={t('common.name')}>
                <Typeahead
                  className="name"
                  initialValue={this.getDefinitionObject(selectedKey)}
                  disabled={noDefinitions}
                  placeholder={t('common.select')}
                  values={availableDefinitions}
                  onSelect={this.changeDefinition}
                  formatter={({name, key}) => name || key}
                  noValuesMessage={t('common.definitionSelection.noDefinition')}
                />
              </Labeled>
              <div className="version entry">
                <Labeled label={t('common.definitionSelection.version.label')} />
                <VersionPopover
                  disabled={!this.hasDefinition()}
                  versions={this.getAvailableVersions(selectedKey)}
                  selected={this.getSelectedVersions()}
                  selectedSpecificVersions={selectedSpecificVersions}
                  onChange={this.changeVersions}
                />
              </div>
              <div className="tenant entry">
                <Labeled label={t('common.definitionSelection.tenant.label')} />
                <TenantPopover
                  tenants={this.getAvailableTenants(selectedKey)}
                  selected={this.getSelectedTenants()}
                  onChange={this.changeTenants}
                />
              </div>
            </div>
            {displayVersionWarning ? (
              <InfoMessage>{t('common.definitionSelection.versionWarning')}</InfoMessage>
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
