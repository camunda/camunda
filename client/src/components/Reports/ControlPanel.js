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
    if (reportLabelMap.objectToKey(this.props.view, 'view') !== reportLabelMap.objectToKey(prevProps.view, 'view')
    ||  reportLabelMap.objectToKey(this.props.groupBy, 'groupBy') !== reportLabelMap.objectToKey(prevProps.groupBy, 'groupBy')) {
      this.checkCombination();
    }
  }

  checkCombination = () => {
    const allowedOptionsMatrix = reportLabelMap.getAllowedOptions();
    const view = reportLabelMap.objectToKey(this.props.view, 'view');
    const groupBy = reportLabelMap.objectToKey(this.props.groupBy, 'groupBy')
    const visualization = reportLabelMap.objectToKey(this.props.visualization, 'visualization')
    if(!allowedOptionsMatrix[view]) {
      this.props.onChange({
        view: reportLabelMap.keyToObject('', 'view'),
        groupBy: reportLabelMap.keyToObject('', 'groupBy'),
        visualization: reportLabelMap.keyToObject('', 'visualization')
      });
      return;
    }

    if(!allowedOptionsMatrix[view][groupBy]) {
      this.props.onChange({
        groupBy: reportLabelMap.keyToObject('', 'groupBy'),
        visualization: reportLabelMap.keyToObject('', 'visualization')
      });
      return;
    }

    if(!allowedOptionsMatrix[view][groupBy].includes(visualization)) {
      this.props.onChange({
        visualization: reportLabelMap.keyToObject('', 'visualization')
      });
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
            {this.isGroupBySelected() && this.isViewSelected() && renderOptions('visualization')}
          </Select>
        </li>
        <li className='ControlPanel__item ControlPanel__item--filter'>
          <Filter data={this.props.filter} onChange={this.props.onChange} {...this.definitionConfig()} xml={this.props.configuration.xml} />
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
