import React from 'react';

import {Select, BPMNDiagram} from 'components';

import './ProcessDefinitionSelection.css';

export default class ControlPanel extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
        availableDefinitions: [],
        loaded: false,
        id: null,
        key: null,
        version: null
    };

    this.loadAvailableDefinitions();
  }

  loadAvailableDefinitions = async () => {

    const availableDefinitions = await this.props.loadProcessDefinitions();
    let key, id, version;

    if(this.props.processDefinitionId) {
      const selectedKeyGroup = availableDefinitions
        .find( group => group.versions.find( def => def.id === this.props.processDefinitionId));
      const selectedDefinition = selectedKeyGroup.versions.find( def => def.id === this.props.processDefinitionId);

      version = selectedDefinition.version;
      key = selectedDefinition.key;
      id = selectedDefinition.id;
    }

    this.setState({
      availableDefinitions, 
      id, key, version,
      loaded: true      
    });
  }  

  changeDefinition = evt => {
    this.props.onChange('processDefinitionId', evt.target.value);
  }

  changeKey = async evt => {
    const key = evt.target.value;
    const selectedKeyGroup = this.findSelectedKeyGroup(key);
    const selectedDefinition = selectedKeyGroup.versions[0];
    
    const id = selectedDefinition.id;
    const version = selectedDefinition.version;
    this.props.onChange('processDefinitionId', id);
    this.setState({key, id, version});
  }

  changeVersion = async evt => {
    const version =  parseInt(evt.target.value, 10);
    const key = this.state.key;
    const selectedDefinition = 
      this.findSelectedKeyGroup(key)
        .versions.find( def => def.version === version);
    
    const id = selectedDefinition.id;
    this.props.onChange('processDefinitionId', id);
    this.setState({id, version});
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
              {<Select.Option defaultValue disabled value=''>Please select...</Select.Option>}
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