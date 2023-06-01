/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import classnames from 'classnames';
import {withRouter} from 'react-router-dom';

import {
  Message,
  BPMNDiagram,
  LoadingIndicator,
  CarbonPopover,
  Typeahead,
  Labeled,
} from 'components';
import {withErrorHandling} from 'HOC';
import {getCollection} from 'services';
import {t} from 'translation';
import {showError} from 'notifications';
import debouncePromise from 'debouncePromise';

import MultiDefinitionSelection from './MultiDefinitionSelection';
import TenantPopover from './TenantPopover';
import VersionPopover from './VersionPopover';
import {loadDefinitions, loadVersions, loadTenants} from './service';

import './DefinitionSelection.scss';

const debounceRequest = debouncePromise();

const defaultSelection = (props = {}) => ({
  key: props.definitionKey || '',
  versions: props.versions || [],
  tenantIds: props.tenants || [],
  name: '',
  identifier: 'definition',
});

export class DefinitionSelection extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      availableDefinitions: null,
      availableVersions: null,
      availableTenants: null,
      isLoadingVersions: false,
      isLoadingTenants: false,
      selection: defaultSelection(props),
      selectedSpecificVersions: this.isSpecificVersion(props.versions) ? props.versions : [],
    };
  }

  componentDidMount = () => {
    const {definitionKey, versions} = this.props;

    this.loadDefinitions().then((availableDefinitions) => this.setState({availableDefinitions}));

    if (definitionKey) {
      this.loadVersions(definitionKey).then((availableVersions) =>
        this.setState({availableVersions})
      );
    }
    if (definitionKey && versions?.length) {
      this.loadTenants(definitionKey, versions).then((availableTenants) =>
        this.setState({availableTenants})
      );
    }
  };

  loadDefinitions = () => {
    return new Promise((resolve) => {
      const {type, camundaEventImportedOnly, location, mightFail} = this.props;
      const collectionId = getCollection(location.pathname);

      mightFail(
        loadDefinitions(type, collectionId, camundaEventImportedOnly),
        (result) =>
          resolve(
            result.map((entry) => ({
              ...entry,
              id: entry.key,
            }))
          ),
        showError
      );
    });
  };

  loadVersions = (key) => {
    return new Promise((resolve) => {
      const {type, location, mightFail} = this.props;
      const collectionId = getCollection(location.pathname);

      mightFail(loadVersions(type, collectionId, key), resolve, showError);
    });
  };

  loadTenants = (key, versions) => {
    return new Promise((resolve) => {
      if (versions.length === 0) {
        resolve([]);
      }
      const {type, location, mightFail} = this.props;
      const collectionId = getCollection(location.pathname);

      mightFail(
        loadTenants(type, [{key, versions}], collectionId),
        (tenantInfo) => resolve(tenantInfo[0].tenants),
        showError
      );
    });
  };

  changeDefinition = async (key) => {
    const {key: definitionKey} = this.state.selection;

    if (definitionKey === key) {
      return;
    }

    this.setState({isLoadingVersions: true, isLoadingTenants: true});

    const allVersions = ['all'];
    const {availableVersions, availableTenants} = await debounceRequest(async () => {
      const availableVersions = await this.loadVersions(key);
      const availableTenants = await this.loadTenants(key, allVersions);

      return {availableVersions, availableTenants};
    });

    const newSelection = {
      key,
      versions: allVersions,
      tenantIds: availableTenants.map(({id}) => id),
      name: this.getName(key),
      identifier: 'definition', // this component only allows selection of a single definition, so we keep the identifier static
    };

    this.setState({
      availableVersions,
      availableTenants,
      selectedSpecificVersions: [availableVersions[0].version],
      selection: newSelection,
      isLoadingVersions: false,
      isLoadingTenants: false,
    });

    this.onChange(newSelection);
  };

  changeVersions = async (versions) => {
    const {selection} = this.state;
    const {key: definitionKey, tenantIds: tenants} = selection;

    if (this.isSpecificVersion(versions)) {
      this.setState({selectedSpecificVersions: versions});
    }

    this.setState({
      isLoadingVersions: true,
      isLoadingTenants: true,
      selection: {...selection, versions},
    });

    const availableTenants = await debounceRequest(this.loadTenants, 0, definitionKey, versions);

    // remove previously deselected tenants from the available tenants of the new version
    const prevTenants = this.state.availableTenants;
    const deselectedTenants = prevTenants
      ?.map(({id}) => id)
      .filter((tenant) => !tenants?.includes(tenant));
    const tenantIds = availableTenants
      ?.map(({id}) => id)
      .filter((tenant) => !deselectedTenants?.includes(tenant));

    const newSelection = {
      key: definitionKey,
      versions,
      tenantIds,
      name: this.getName(definitionKey),
      identifier: 'definition',
    };

    this.setState({
      availableTenants,
      selection: newSelection,
      isLoadingTenants: false,
      isLoadingVersions: false,
    });

    this.onChange(newSelection);
  };

  changeTenants = async (tenantSelection) => {
    this.setState({isLoadingTenants: true});

    const newSelection = {
      ...this.state.selection,
      tenantIds: tenantSelection,
    };

    this.setState({selection: newSelection});
    await this.onChange(newSelection);
    this.setState({isLoadingTenants: false});
  };

  onChange = (newSelection) =>
    this.props.onChange(this.props.selectedDefinitions ? [newSelection] : newSelection);

  hasDefinition = () => this.state.selection.key;
  hasTenants = () => {
    const {key, versions} = this.state.selection;
    if (key && versions) {
      const tenants = this.getAvailableTenants();
      return tenants?.length > 1;
    }
    return false;
  };

  getName = (key) => this.getDefinitionObject(key).name;
  getDefinitionObject = (key) => this.state.availableDefinitions.find((def) => def.key === key);
  getAvailableVersions = () => this.state.availableVersions;
  getSelectedVersions = () => this.state.selection.versions || [];
  getAvailableTenants = () => this.state.availableTenants;
  getSelectedTenants = () => this.state.selection.tenantIds;

  isSpecificVersion = (versions) => versions && versions[0] !== 'latest' && versions[0] !== 'all';
  canRenderDiagram = () => this.props.renderDiagram && this.props.xml;

  createTitle = () => {
    const {key: definitionKey, versions} = this.state.selection;
    const {type, selectedDefinitions} = this.props;

    if (selectedDefinitions?.length > 1) {
      return;
    }

    if (definitionKey && versions && this.getAvailableTenants()) {
      const availableTenants = this.getAvailableTenants();
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
        tenant = tenantObj?.name || tenantObj?.id;
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

  resetSelection = (props) => {
    this.setState({selection: defaultSelection(props)});
  };

  render() {
    const {
      availableDefinitions,
      selectedSpecificVersions,
      selection,
      isLoadingVersions,
      isLoadingTenants,
    } = this.state;
    const {expanded, type, disableDefinition, selectedDefinitions, onChange, versionTooltip} =
      this.props;
    const collectionId = getCollection(this.props.location.pathname);
    const noDefinitions = !availableDefinitions || availableDefinitions.length === 0;
    const selectedKey = selection.key;
    const versions = this.getSelectedVersions();
    const displayVersionWarning = versions.length > 1 || versions[0] === 'all';

    if (!availableDefinitions) {
      return (
        <div className="DefinitionSelection">
          <LoadingIndicator small />
        </div>
      );
    }

    const def = this.getDefinitionObject(selectedKey);
    const Wrapper = expanded ? 'div' : CarbonPopover;
    const processSelectLabel = expanded
      ? t(`common.definitionSelection.select.${type}`)
      : t('common.name');

    return (
      <Wrapper className="DefinitionSelection" title={this.createTitle()}>
        <div
          className={classnames('container', {
            large: this.canRenderDiagram(),
            withTenants: this.hasTenants(),
          })}
        >
          <div className="selectionPanel">
            <div className="dropdowns">
              {selectedDefinitions ? (
                <MultiDefinitionSelection
                  selectedDefinitions={selectedDefinitions}
                  availableDefinitions={availableDefinitions}
                  changeDefinition={this.changeDefinition}
                  resetSelection={this.resetSelection}
                  onChange={onChange}
                />
              ) : (
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
              )}
              <div className="version entry">
                <Labeled label={t('common.definitionSelection.version.label')} />
                <VersionPopover
                  tooltip={versionTooltip}
                  disabled={!this.hasDefinition()}
                  versions={this.getAvailableVersions()}
                  selected={this.getSelectedVersions()}
                  selectedSpecificVersions={selectedSpecificVersions}
                  onChange={this.changeVersions}
                  loading={isLoadingVersions}
                />
              </div>
              <div className="tenant entry">
                <Labeled label={t('common.tenant.label')} />
                <TenantPopover
                  tenants={this.getAvailableTenants()}
                  selected={this.getSelectedTenants()}
                  onChange={this.changeTenants}
                  loading={isLoadingTenants}
                />
              </div>
            </div>
            <div className="info">
              {displayVersionWarning &&
                (!selectedDefinitions || selectedDefinitions.length === 1) && (
                  <Message>{t('common.definitionSelection.versionWarning')}</Message>
                )}
              {collectionId && noDefinitions && (
                <Message>{t('common.definitionSelection.noSourcesWarning')}</Message>
              )}
              {selectedDefinitions?.length >= 10 && (
                <Message error>{t('common.definitionSelection.limitReached')}</Message>
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

export default withRouter(withErrorHandling(DefinitionSelection));
