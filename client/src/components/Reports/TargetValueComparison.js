import React from 'react';
import update from 'immutability-helper';

import Viewer from 'bpmn-js/lib/NavigatedViewer';
import {ButtonGroup, Button, Modal, BPMNDiagram, Table, Input, Select} from 'components';
import settingsIcon from './settings.svg';
import {formatters} from 'services';

import './TargetValueComparison.css';

export default class TargetValueComparison extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      modalOpen: false,
      values: {},
      nodeNames: {}
    };
  }

  getConfig = () => {
    return (this.props.configuration.targetValue) || {};
  }

  toggleMode = () => {
    const {active, values} = this.getConfig();

    if(active) {
      this.setActive(false);
    } else if(!values || Object.keys(values).length === 0) {
      this.openModal();
    } else {
      this.setActive(true);
    }
  }

  setActive = active => {
    this.props.onChange({
      configuration: {
        ...this.props.configuration,
        targetValue: {
          ...this.getConfig(),
          active
        }
      }
    });
  }

  openModal = async () => {
    const {values, nodeNames} = await this.constructValues();

    this.setState({
      modalOpen: true,
      values,
      nodeNames
    });
  }

  closeModal = () => {
    this.setState({
      modalOpen: false,
      values: {},
      nodeNames: {}
    });
  }

  confirmModal = () => {
    const values = this.cleanUpValues();

    this.props.onChange({
      configuration: {
        ...this.props.configuration,
        targetValue: {
          active: Object.keys(values).length > 0,
          values
        }
      }
    });
    this.closeModal();
  }

  cleanUpValues = () => {
    // this function removes all entries without value and converts values into numbers
    const values = {};

    Object.keys(this.state.values).forEach(key => {
      const entry = this.state.values[key];
      if(entry && entry.value.trim()) {
        values[key] = {
          value: parseFloat(entry.value),
          unit: entry.unit
        };
      }
    });

    return values;
  }

  constructValues = () => {
    return new Promise(resolve => {
      const viewer = new Viewer();
      viewer.importXML(this.props.configuration.xml, () => {
        const predefinedValues = this.getConfig().values || {};
        const values = {};
        const nodeNames = {};

        new Set(viewer.get('elementRegistry')
          .filter(element => element.businessObject.$instanceOf('bpmn:FlowNode'))
          .map(element => element.businessObject)
        ).forEach(element => {
          values[element.id] = this.copyObjectIfExistsAndStringifyValue(predefinedValues[element.id]);
          nodeNames[element.id] = element.name || element.id;
        });

        resolve({values, nodeNames});
      });
    });
  }

  copyObjectIfExistsAndStringifyValue = obj => {
    if(obj) {
      return {
        ...obj,
        value: '' + obj.value
      };
    }
    return obj;
  }

  setTarget = (type, id) => ({target: {value}}) => {
    if(this.state.values[id]) {
      this.setState(update(this.state, {
        values: {
          [id]: {
            [type]: {$set: value}
          }
        }
      }));
    } else {
      this.setState(update(this.state, {
        values: {
          [id]: {$set: {
            value: '0',
            unit: 'hours',
            [type]: value
          }}
        }
      }));
    }
  }

  constructTableBody = () => {
    return Object.keys(this.state.values).map(id => {
      const settings = this.state.values[id] || {value: '', unit: 'hours'};
      return [
        this.state.nodeNames[id],
        formatters.duration(this.props.reportResult.result[id] || 0),
        <React.Fragment>
          <Input value={settings.value} onChange={this.setTarget('value', id)} />
          <Select value={settings.unit} onChange={this.setTarget('unit', id)}>
            <Select.Option value='millis'>Milliseconds</Select.Option>
            <Select.Option value='seconds'>Seconds</Select.Option>
            <Select.Option value='minutes'>Minutes</Select.Option>
            <Select.Option value='hours'>Hours</Select.Option>
            <Select.Option value='days'>Days</Select.Option>
            <Select.Option value='weeks'>Weeks</Select.Option>
            <Select.Option value='months'>Months</Select.Option>
            <Select.Option value='years'>Years</Select.Option>
          </Select>
        </React.Fragment>
      ];
    });
  }

  isEnabled = () => {
    const {processDefinitionKey, processDefinitionVersion, view, groupBy, visualization} = this.props.reportResult.data;

    return processDefinitionKey && processDefinitionVersion &&
      view.entity === 'flowNode' && view.operation === 'avg' && view.property === 'duration' &&
      groupBy.type === 'flowNode' &&
      visualization === 'heat';
  }

  validChanges = () => {
    return this.hasSomethingChanged() && this.areAllFieldsNumbers();
  }

  hasSomethingChanged = () => {
    const prev = this.getConfig().values || {};
    const now = this.cleanUpValues();

    return JSON.stringify(prev) !== JSON.stringify(now);
  }

  areAllFieldsNumbers = () => {
    return Object.keys(this.state.values)
      .filter(key => this.state.values[key])
      .every(key => {
        const entry = this.state.values[key];
        const value = entry && entry.value;

        return value.trim() === '' || (!isNaN(value.trim()) && (+value > 0));
    });
  }

  render() {
    const isEnabled = this.isEnabled();

    return (<ButtonGroup>
      <Button className='TargetValueComparison__toggleButton' disabled={!isEnabled} active={this.getConfig().active} onClick={this.toggleMode}>Target Value</Button>
      <Button className='TargetValueComparison__editButton' disabled={!isEnabled} onClick={this.openModal}><img src={settingsIcon} alt="settings" className='TargetValueComparison__settingsIcon' /></Button>
      <Modal open={this.state.modalOpen} onClose={this.closeModal} className='TargetValueComparison__Modal' size='large'>
        <Modal.Header>Target Value Comparison</Modal.Header>
        <Modal.Content>
          <div className='TargetValueComparison__DiagramContainer'>
            <BPMNDiagram xml={this.props.configuration.xml} />
          </div>
          <Table
            head={['Activity', 'Actual Value', 'Target Value']}
            body={this.constructTableBody()}
            foot={[]}
            className='TargetValueComparison__Table'
          />
        </Modal.Content>
        <Modal.Actions>
          <Button onClick={this.closeModal}>Cancel</Button>
          <Button type="primary" color="blue" onClick={this.confirmModal} disabled={!this.validChanges()}>Apply</Button>
        </Modal.Actions>
      </Modal>
    </ButtonGroup>);
  }
}
