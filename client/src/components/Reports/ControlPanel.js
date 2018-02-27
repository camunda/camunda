import React from 'react';
import {Select, Popover, ProcessDefinitionSelection} from 'components';

import {Filter} from './filter';
import {reportLabelMap} from 'services';

import {TargetValueComparison} from './targetValue';

import './ControlPanel.css';

export default class ControlPanel extends React.Component {

  changeView = evt => {
    const viewKey = evt.target.value;
    this.props.onChange({
      view: reportLabelMap.keyToObject(viewKey, reportLabelMap.view),
      configuration: {...this.props.configuration, targetValue: {...this.props.configuration.targetValue, active: false}}
    });
  }
  changeGroup = evt => {
    const groupByKey = evt.target.value;
    this.props.onChange({
      groupBy: reportLabelMap.keyToObject(groupByKey, reportLabelMap.groupBy),
      configuration: {...this.props.configuration, targetValue: {...this.props.configuration.targetValue, active: false}}
    });
  }

  changeVisualization = evt => {
    this.props.onChange({
      visualization: evt.target.value,
      configuration: {...this.props.configuration, targetValue: {...this.props.configuration.targetValue, active: false}}
    });
  }

  componentDidUpdate(prevProps) {
    if (this.props.groupBy !== prevProps.groupBy) {
      this.checkCombination('groupBy');
    } else if(this.props.view !== prevProps.view) {
      this.checkCombination('view')
    }
  }

  checkCombination = type => {
    const option = reportLabelMap.getOptions(type).find((v) => {
      return v.key === reportLabelMap.objectToKey(this.props[type], type);
    });
    const nextFieldObject = this.props[this.getNextField(type)];
    const nextFieldType = this.getNextField(type);
    const nextFieldKey = reportLabelMap.objectToKey(nextFieldObject, nextFieldType);

    if (option && !option.allowedNext.includes(nextFieldKey)) {
      if(this.getNextField(nextFieldType)) {
        this.props.onChange({
          [nextFieldType]: reportLabelMap.keyToObject('', nextFieldType),
          [this.getNextField(nextFieldType)]: reportLabelMap.keyToObject('', this.getNextField(nextFieldType))
        });
      } else {
        this.props.onChange({[nextFieldType]: ''});
      }
    }
  }

  getNextField = (type) => {
    switch (type) {
      case 'groupBy': return 'visualization';
      case 'view':  return 'groupBy';
      default: return null;
    }
  }

  definitionConfig = () => {
    return {
      processDefinitionKey: this.props.processDefinitionKey,
      processDefinitionVersion: this.props.processDefinitionVersion
    };
  }

  createTitle = () => {
    const {processDefinitionKey, processDefinitionVersion} = this.props;
    if(processDefinitionKey && processDefinitionVersion) {
      return `${processDefinitionKey} : ${processDefinitionVersion}`;
    } else {
      return 'Select Process Definition';
    }
  }

  isEmpty = (prop) => {
    if (typeof(prop) === 'object') {
      let result = false;
      if (Object.keys(prop).length === 0) return true;
      Object.values(prop).forEach((str) => {
        if (str !== null && this.isEmpty(str)) {
          result = true;
        };
      });
      return result;
    } else if(typeof(prop) === 'string') {
      return prop === '';
    }
  }

  isViewSelected = () => {
    return !this.isEmpty(this.props.view);
  }

  isGroupBySelected = () => {
    return !this.isEmpty(this.props.groupBy);
  }

  render() {
    return <div className='ControlPanel'>
      <ul className='ControlPanel__list'>
        <li className='ControlPanel__item ControlPanel__item--select'>
          <label htmlFor='ControlPanel__process-definition' className='ControlPanel__label'>Process definition</label>
          <Popover className='ControlPanel__popover' title={ this.createTitle()}>
            <ProcessDefinitionSelection {...this.definitionConfig()} xml={this.props.configuration.xml} 
              onChange={this.props.onChange} renderDiagram={true} enableAllVersionSelection={true}/>
          </Popover>
        </li>
        <li className='ControlPanel__item ControlPanel__item--select'>
          <label htmlFor='ControlPanel__view' className='ControlPanel__label'>View</label>
          <Select className='ControlPanel__select' name='ControlPanel__view' value={reportLabelMap.objectToKey(this.props.view, reportLabelMap.view)} onChange={this.changeView}>
            {addSelectionOption()}
            {renderOptions('view')}
          </Select>
        </li>
        <li className='ControlPanel__item ControlPanel__item--select'>
          <label htmlFor='ControlPanel__group-by' className='ControlPanel__label'>Group by</label>
          <Select disabled={!this.isViewSelected()} className='ControlPanel__select' name='ControlPanel__group-by' value={reportLabelMap.objectToKey(this.props.groupBy, reportLabelMap.groupBy)} onChange={this.changeGroup}>
            {addSelectionOption()}
            {this.isViewSelected() && renderOptions('groupBy')}
          </Select>
        </li>
        <li className='ControlPanel__item ControlPanel__item--select'>
          <label htmlFor='ControlPanel__visualize-as' className='ControlPanel__label'>Visualize as</label>
          <Select disabled={!this.isGroupBySelected() || !this.isViewSelected()} className='ControlPanel__select' name='ControlPanel__visualize-as' value={this.props.visualization} onChange={this.changeVisualization}>
            {addSelectionOption()}
            {this.isGroupBySelected() && this.isViewSelected() && renderOptions('visualizeAs')}
          </Select>
        </li>
        <li className='ControlPanel__item ControlPanel__item--filter'>
          <Filter data={this.props.filter} onChange={this.props.onChange} processDefinitionId={this.props.processDefinitionId} xml={this.props.configuration.xml} />
        </li>
        <li className='ControlPanel__item'>
          <TargetValueComparison reportResult={this.props.reportResult} configuration={this.props.configuration} onChange={this.props.onChange} />
        </li>
      </ul>
    </div>
  }
}

function addSelectionOption() {
  return <Select.Option value=''>Please select...</Select.Option>;
}

function renderOptions(type) {
  const options = reportLabelMap.getOptions(type);

  return options.map(({key, label}) => {
    return <Select.Option key={key} value={key}>{label}</Select.Option>;
  });
}
