/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import classnames from 'classnames';

import {Message, BPMNDiagram, LoadingIndicator, Popover, Typeahead, Labeled} from 'components';

import {loadDefinitions, getCollection} from 'services';
import {withRouter} from 'react-router-dom';

import TenantPopover from './TenantPopover';
import VersionPopover from './VersionPopover';

import './DefinitionSelection.scss';
import {t} from 'translation';

export default withRouter(
  class DefinitionSelection extends React.Component {
    constructor(props) {
      super(props);

      this.state = {
        availableDefinitions: [],
        selectedSpecificVersions: this.isSpecificVersion(props.versions) ? props.versions : [],
        loaded: false
      };
    }

    componentDidMount = async () => {
      const collectionId = getCollection(this.props.location.pathname);
      this.setState({
        availableDefinitions: (await loadDefinitions(this.props.type, collectionId)).map(entry => ({
          ...entry,
          id: entry.key
        })),
        loaded: true
      });
    };

    hasDefinition = () => this.props.definitionKey;

    getName = key => this.getDefinitionObject(key).name;

    changeDefinition = key => {
      if (this.props.definitionKey === key) {
        return;
      }
      const definitionObject = this.getDefinitionObject(key);
      const latestDefinition = definitionObject.versions[0];
      const latestVersion = [latestDefinition.version];
      const tenants = this.getAvailableTenants(key, latestVersion);

      this.setState({selectedSpecificVersions: latestVersion});
      this.props.onChange({
        key,
        versions: latestVersion,
        tenantIds: tenants.map(({id}) => id),
        name: this.getName(key)
      });
    };

    getDefinitionObject = key => this.state.availableDefinitions.find(def => def.key === key);
    canRenderDiagram = () => this.props.renderDiagram && this.props.xml;

    getAvailableTenants = (key, versions) => {
      const definitionObject = this.getDefinitionObject(key);
      if (versions && versions.length === 1 && versions[0] === 'all') {
        return definitionObject.allTenants;
      }
      if (definitionObject && versions) {
        const specificVersions =
          versions[0] === 'latest' ? [definitionObject.versions[0].version] : versions;
        const allTenantsWithDuplicates = definitionObject.versions
          .filter(versionEntry => specificVersions.includes(versionEntry.version))
          .map(versionEntry => versionEntry.tenants)
          .flat();
        return this.filterDuplicateTenants(allTenantsWithDuplicates);
      }
      return [];
    };

    filterDuplicateTenants = arrayOfTenantsWithDuplicates => {
      return arrayOfTenantsWithDuplicates.filter(
        (object, index, self) => index === self.findIndex(o => o.id === object.id)
      );
    };

    hasTenants = () => {
      if (this.props.definitionKey && this.props.versions) {
        const tenants = this.getAvailableTenants(this.props.definitionKey, this.props.versions);
        return tenants.length > 1;
      }
      return false;
    };

    getSelectedTenants = () => this.props.tenants;

    changeTenants = tenantSelection => {
      const {definitionKey, versions} = this.props;
      this.props.onChange({
        key: definitionKey,
        versions: versions,
        tenantIds: tenantSelection,
        name: this.getName(definitionKey)
      });
    };

    getAvailableVersions = key => {
      const definitionObject = this.getDefinitionObject(key);
      if (definitionObject) {
        return definitionObject.versions.map(({version, versionTag}) => ({version, versionTag}));
      }
      return [];
    };

    getSelectedVersions = () => this.props.versions || [];

    changeVersions = versions => {
      if (this.isSpecificVersion(versions)) {
        this.setState({selectedSpecificVersions: versions});
      }

      this.props.onChange({
        key: this.props.definitionKey,
        versions,
        tenantIds: this.findTenants(versions),
        name: this.getName(this.props.definitionKey)
      });
    };

    findTenants = versions => {
      const prevTenants = this.getAvailableTenants(this.props.definitionKey, this.props.versions);
      const deselectedTenants = prevTenants
        .map(({id}) => id)
        .filter(tenant => !this.props.tenants.includes(tenant));

      // remove previously deselected tenants from the available tenants of the new version
      const newTenants = this.getAvailableTenants(this.props.definitionKey, versions)
        .map(({id}) => id)
        .filter(tenant => !deselectedTenants.includes(tenant));

      return newTenants;
    };

    isSpecificVersion = versions => versions && versions[0] !== 'latest' && versions[0] !== 'all';

    createTitle = () => {
      const {definitionKey, versions, type} = this.props;

      if (definitionKey && versions) {
        const availableTenants = this.getAvailableTenants(definitionKey, versions);
        const selectedTenants = this.getSelectedTenants();

        const definition = this.getDefinitionObject(definitionKey);
        const definitionName = definition.name || definition.key;

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
          const tenantObj = availableTenants.find(({id}) => id === selectedTenants[0]);
          tenant = tenantObj.name || tenantObj.id;
        }

        if (tenant) {
          return `${definitionName} : ${versionString} : ${tenant}`;
        } else {
          return `${definitionName} : ${versionString}`;
        }
      } else {
        return t(`common.definitionSelection.select.${type}`);
      }
    };

    render() {
      const {loaded, availableDefinitions, selectedSpecificVersions} = this.state;
      const {expanded, type, disableDefinition} = this.props;
      const collectionId = getCollection(this.props.location.pathname);
      const noDefinitions = !availableDefinitions || availableDefinitions.length === 0;
      const selectedKey = this.props.definitionKey;
      const versions = this.getSelectedVersions();
      const displayVersionWarning = versions.length > 1 || versions[0] === 'all';

      if (!loaded) {
        return (
          <div className="DefinitionSelection">
            <LoadingIndicator small />
          </div>
        );
      }

      const def = this.getDefinitionObject(selectedKey);
      const Wrapper = expanded ? 'div' : Popover;
      const processSelectLabel = expanded
        ? t(`common.definitionSelection.select.${type}`)
        : t('common.name');

      return (
        <Wrapper className="DefinitionSelection" title={this.createTitle()}>
          <div
            className={classnames('container', {
              large: this.canRenderDiagram(),
              withTenants: this.hasTenants()
            })}
          >
            <div className="selectionPanel">
              <div className="dropdowns">
                <Labeled className="entry" label={processSelectLabel}>
                  <Typeahead
                    className="name"
                    initialValue={def ? def.key : null}
                    disabled={noDefinitions || disableDefinition}
                    placeholder={t('common.select')}
                    onChange={this.changeDefinition}
                    noValuesMessage={t('common.definitionSelection.noDefinition')}
                  >
                    {availableDefinitions.map(({name, key}) => (
                      <Typeahead.Option key={key} value={key}>
                        {name || key}
                      </Typeahead.Option>
                    ))}
                  </Typeahead>
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
                  <Labeled label={t('common.tenant.label')} />
                  <TenantPopover
                    tenants={this.getAvailableTenants(selectedKey, versions)}
                    selected={this.getSelectedTenants()}
                    onChange={this.changeTenants}
                  />
                </div>
              </div>
              <div className="info">
                {displayVersionWarning && (
                  <Message>{t('common.definitionSelection.versionWarning')}</Message>
                )}
                {collectionId && noDefinitions && (
                  <Message>{t('common.definitionSelection.noSourcesWarning')}</Message>
                )}
                {this.props.infoMessage && <Message>{this.props.infoMessage}</Message>}
              </div>
            </div>
            {this.canRenderDiagram() && (
              <div className="diagram">
                <hr />
                <BPMNDiagram xml={this.props.xml} disableNavigation />
              </div>
            )}
          </div>
        </Wrapper>
      );
    }
  }
);
