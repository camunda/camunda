import React from 'react';

import ControlPanel from './ControlPanel';
import {BPMNDiagram} from 'components';

import {loadProcessDefinitionXml, loadFrequencyData} from './service';
import DiagramBehavior from './DiagramBehavior';
import Statistics from './Statistics';

import './Analysis.css';

export default class Analysis extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      config: {
        processDefinitionId: '',
        processDefinitionKey: '',
        processDefinitionVersion: '',
        filter: []
      },
      data: null,
      hoveredControl: null,
      hoveredNode: null,
      gateway: null,
      endEvent: null,
      xml: null
    };
  }

  render() {
    const {
      xml,
      config,
      hoveredControl,
      hoveredNode,
      gateway,
      endEvent,
      data
    } = this.state;

    return <div className='Analysis'>
      <ControlPanel
        {...config}
        hoveredControl={hoveredControl}
        hoveredNode={hoveredNode}
        onChange={this.updateConfig}
        gateway={gateway}
        endEvent={endEvent}
        updateHover={this.updateHoveredControl}
        updateSelection={this.updateSelection}
        xml={xml}
        />
      <div className='Analysis__diagram'>
        {xml && <BPMNDiagram xml={xml}>
          <DiagramBehavior
            hoveredControl={hoveredControl}
            hoveredNode={hoveredNode}
            updateHover={this.updateHoveredNode}
            updateSelection={this.updateSelection}
            gateway={gateway}
            endEvent={endEvent}
            data={data}
          />
        </BPMNDiagram>}
      </div>
      {
        gateway && endEvent &&
        <Statistics
          gateway={gateway}
          endEvent={endEvent}
          config={config}
        />
      }
    </div>;
  }

  async componentDidUpdate(prevProps, prevState) {
    const {config} = this.state;
    const {config: prevConfig} = prevState;
    if(config.processDefinitionId &&
        (prevConfig.filter !== config.filter ||
         prevConfig.processDefinitionId !== config.processDefinitionId)) {

      this.setState({data: await loadFrequencyData(config.processDefinitionId, config.filter)});
    }
  }

  updateHoveredControl = newField => {
    this.setState({hoveredControl: newField});
  }

  updateHoveredNode = newNode => {
    this.setState({hoveredNode: newNode});
  }

  updateSelection = (type, node) => {
    this.setState({[type]: node});
  }

  updateConfig = async (updates) => {
    const config = {
      ...this.state.config
    };
    updates.forEach( ({field, newValue}) => {
      config[field] = newValue;
    });
    this.setState({config});

    const processDefinitionIdWasUpdated = updates.find(({field}) => field === 'processDefinitionId');
    if(processDefinitionIdWasUpdated && processDefinitionIdWasUpdated.newValue) {
      this.setState({
        xml: await loadProcessDefinitionXml(processDefinitionIdWasUpdated.newValue),
        gateway: null,
        endEvent: null
      });
    }
  }
}
