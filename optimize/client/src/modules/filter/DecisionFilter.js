/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Component} from 'react';
import {MenuItem} from '@carbon/react';
import {MenuDropdown} from '@camunda/camunda-optimize-composite-components';
import update from 'immutability-helper';

import {DateFilter, VariableFilter} from './modals';

import FilterList from './FilterList';
import './Filter.scss';

import {loadDecisionValues, filterSameTypeExistingFilters} from './service';
import {t} from 'translation';

import './DecisionFilter.scss';

export default class DecisionFilter extends Component {
  state = {
    newFilterType: null,
    editFilter: null,
  };

  openNewFilterModal = (type) => () => {
    this.setState({newFilterType: type});
  };

  openEditFilterModal = (filter) => () => {
    if (filter.type === 'inputVariable' || filter.type === 'outputVariable') {
      const variable = this.props.variables[filter.type].find(({id}) => filter.data.name === id);

      this.setState({
        editFilter: update(filter, {
          data: {
            id: {$set: variable.id},
            name: {$set: variable.name},
          },
        }),
      });
    } else {
      this.setState({editFilter: filter});
    }
  };

  closeModal = () => {
    this.setState({newFilterType: null, editFilter: null});
  };

  getFilterModal = (type) => {
    switch (type) {
      case 'evaluationDateTime':
        return DateFilter;
      case 'inputVariable':
      case 'outputVariable':
        return VariableFilter;
      default:
        return () => null;
    }
  };

  getFilterConfig = (type) => {
    if (type === 'inputVariable' || type === 'outputVariable') {
      return {
        getVariables: () => this.props.variables[type],
        getValues: async (id, varType, numResults, valueFilter) =>
          await loadDecisionValues(
            type,
            this.props.decisionDefinitionKey,
            this.props.decisionDefinitionVersions,
            this.props.tenants,
            id,
            varType,
            0,
            numResults,
            valueFilter
          ),
      };
    }
  };

  editFilter = (newFilter) => {
    const filters = this.props.data;

    let index;
    if (newFilter.type === 'inputVariable' || newFilter.type === 'outputVariable') {
      index = filters.indexOf(filters.find((v) => this.state.editFilter.data.id === v.data.name));
    } else {
      index = filters.indexOf(filters.find((v) => this.state.editFilter.data === v.data));
    }

    this.props.onChange({filter: {[index]: {$set: newFilter}}}, true);
    this.closeModal();
  };

  addFilter = (newFilter) => {
    const filters = filterSameTypeExistingFilters(this.props.data, newFilter);

    this.props.onChange({filter: {$set: [...filters, newFilter]}}, true);
    this.closeModal();
  };

  deleteFilter = (oldFilter) => {
    this.props.onChange(
      {
        filter: {$set: this.props.data.filter((filter) => oldFilter !== filter)},
      },
      true
    );
  };

  definitionIsNotSelected = () => {
    return (
      !this.props.decisionDefinitionKey ||
      this.props.decisionDefinitionKey === '' ||
      !this.props.decisionDefinitionVersions ||
      this.props.decisionDefinitionVersions.length === 0
    );
  };

  render() {
    const {definitions} = this.props;

    const FilterModal = this.getFilterModal(this.state.newFilterType);
    const EditFilterModal = this.getFilterModal(
      this.state.editFilter ? this.state.editFilter.type : null
    );

    const filters = this.props.data;

    return (
      <div className="DecisionFilter Filter">
        <div className="filterHeader">
          <span className="dropdownLabel">{t('common.filter.dropdownLabel.decision')}</span>
          <MenuDropdown
            label={t('common.add')}
            id="ControlPanel__filters"
            className="Filter__dropdown"
          >
            <MenuItem
              label={t('common.filter.types.evaluationDateTime')}
              onClick={this.openNewFilterModal('evaluationDateTime')}
            />
            <MenuItem
              label={t('common.filter.types.inputVariable')}
              disabled={this.definitionIsNotSelected()}
              onClick={this.openNewFilterModal('inputVariable')}
            />
            <MenuItem
              label={t('common.filter.types.outputVariable')}
              disabled={this.definitionIsNotSelected()}
              onClick={this.openNewFilterModal('outputVariable')}
            />
          </MenuDropdown>
        </div>
        {filters.length === 0 && (
          <p className="emptyMessage">{t('common.filter.allVisible.decision')}</p>
        )}
        {filters.length > 1 && <p className="linkingTip">{t('common.filter.linkingTip')}</p>}
        <FilterList
          definitions={definitions}
          openEditFilterModal={this.openEditFilterModal}
          data={filters}
          deleteFilter={this.deleteFilter}
          variables={this.props.variables}
        />
        <FilterModal
          definitions={definitions}
          addFilter={this.addFilter}
          close={this.closeModal}
          filterType={this.state.newFilterType}
          config={this.getFilterConfig(this.state.newFilterType)}
        />
        <EditFilterModal
          definitions={definitions}
          addFilter={this.editFilter}
          filterData={this.state.editFilter}
          close={this.closeModal}
          filterType={this.state.editFilter && this.state.editFilter.type}
          config={this.getFilterConfig(this.state.editFilter && this.state.editFilter.type)}
        />
      </div>
    );
  }
}
