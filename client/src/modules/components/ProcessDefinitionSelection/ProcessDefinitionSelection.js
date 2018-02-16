import React from 'react';

import {Select, BPMNDiagram} from 'components';

import './ProcessDefinitionSelection.css';

export default class ControlPanel extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
        availableDefinitions: [],
        loaded: false,
        id: this.props.processDefinitionId,
        key: this.props.processDefinitionKey,
        version: this.props.processDefinitionVersion
    };

    this.loadAvailableDefinitions();
  }

  loadAvailableDefinitions = async () => {

    const availableDefinitions = await this.props.loadProcessDefinitions();

    this.setState({
      availableDefinitions, 
      loaded: true      
    });
  }  

  propagateChange = (id, key, version) => {
    this.props.onChange({
      'processDefinitionId': id,
      'processDefinitionKey': key,
      'processDefinitionVersion': version
   });   
  }

  changeKey = async evt => {
    const key = evt.target.value;
    const selectedDefinition = this.getLatestDefinition(key);
    
    const id = selectedDefinition.id;
    const version = selectedDefinition.version;
    this.propagateChange(id, key, version);
    this.setState({key, id, version});
  }

  changeVersion = async evt => {
    const version = evt.target.value === 'all'? 'all': parseInt(evt.target.value, 10);
    const key = this.state.key;
    const selectedDefinition = 
      this.findSelectedKeyGroup(key)
        .versions.find( def => def.version === version);
    
    const id = selectedDefinition? selectedDefinition.id :this.getLatestDefinition(key).id;
    this.propagateChange(id, key, version);
    this.setState({id, version});
  }

  getLatestDefinition = (key) => {
    const selectedKeyGroup = this.findSelectedKeyGroup(key);
    return selectedKeyGroup.versions[0];
  }

  findSelectedKeyGroup = (key) => {
    return this.state.availableDefinitions
              .find( def => def.key === key);
  }

  getKey = () => {
    return this.state.key? this.state.key : '';
  }

  getVersion = () => {
    return this.state.version? this.state.version: '';
  }

  render() {
    const {loaded, id, key} = this.state;

    if(!loaded) {
      return <div className='ProcessDefinitionSelection__loading-indicator'>loading...</div>;
    }
    
    return (
      <div className={'ProcessDefinitionSelection' + (this.props.renderDiagram? ' ProcessDefinitionSelection--large' : '')} >
        <ul className='ProcessDefinitionSelection__list'>
          <li className='ProcessDefinitionSelection__list-item'>
            <label htmlFor='ProcessDefinitionSelection__process-definition' className='ProcessDefinitionSelection__label'>Key</label>        
            <Select className='ProcessDefinitionSelection__select' name='ProcessDefinitionSelection__key' value={this.getKey()} onChange={this.changeKey}>
                {<Select.Option defaultValue disabled value=''>Please select...</Select.Option>}
                {this.state.availableDefinitions.map(
                  ({key}) => {
                    return <Select.Option value={key} key={key} >{key}</Select.Option>;
                  })
                }
            </Select>
          </li>
          <li className='ProcessDefinitionSelection__list-item'>
            <label htmlFor='ProcessDefinitionSelection__process-definition' className='ProcessDefinitionSelection__label'>Version</label>                    
            <Select className='ProcessDefinitionSelection__select' name='ProcessDefinitionSelection__version' 
                  value={this.getVersion()} onChange={this.changeVersion} disabled={!key}>
              <Select.Option defaultValue disabled value=''>Please select...</Select.Option>
              { this.props.enableAllVersionSelection && <Select.Option value='all' key='all'>all</Select.Option>}
              {this.renderAllDefinitionVersions(key)}
            </Select>
          </li>
        </ul>
        { this.props.renderDiagram && id &&
          <div className={'ProcessDefinitionSelection__diagram'}> 
              <BPMNDiagram xml={this.props.xml} />
          </div>
        }
      </div>
    );
  }

  renderAllDefinitionVersions = (key) => {
    return (
      key && 
        this.findSelectedKeyGroup(key)
          .versions.map(({version}) => <Select.Option value={version} key={version} >{version}</Select.Option>)
      );
  }

}