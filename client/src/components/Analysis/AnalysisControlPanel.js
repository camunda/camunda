import classnames from 'classnames';
import React from 'react';
import {ActionItem, Popover, ProcessDefinitionSelection} from 'components';

import {extractProcessDefinitionName, getFlowNodeNames} from 'services';

import {Filter} from '../Reports';

import './AnalysisControlPanel.scss';

export default class AnalysisControlPanel extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      processDefinitionName: this.props.processDefinitionKey,
      flowNodeNames: null
    };
  }

  componentDidMount() {
    this.loadProcessDefinitionName();
    this.loadFlowNodeNames();
  }

  loadProcessDefinitionName = () => {
    const {xml} = this.props;
    if (xml) {
      const processDefinitionName = extractProcessDefinitionName(xml);
      this.setState({
        processDefinitionName
      });
    }
  };

  loadFlowNodeNames = async () => {
    this.setState({
      flowNodeNames: await getFlowNodeNames(
        this.props.processDefinitionKey,
        this.props.processDefinitionVersion
      )
    });
  };

  componentDidUpdate({xml, processDefinitionKey, processDefinitionVersion}) {
    if (this.props.xml !== xml) {
      this.loadProcessDefinitionName();
    }

    if (
      this.props.processDefinitionKey !== processDefinitionKey ||
      this.props.processDefinitionVersion !== processDefinitionVersion
    ) {
      this.loadFlowNodeNames();
    }
  }
  getDefinitionConfig = () => {
    return {
      processDefinitionKey: this.props.processDefinitionKey,
      processDefinitionVersion: this.props.processDefinitionVersion
    };
  };

  hover = element => () => {
    this.props.updateHover(element);
  };

  createTitle = () => {
    const {processDefinitionKey, processDefinitionVersion} = this.props;
    const processDefintionName = this.state.processDefinitionName
      ? this.state.processDefinitionName
      : processDefinitionKey;
    if (processDefintionName && processDefinitionVersion) {
      return `${processDefintionName} : ${processDefinitionVersion}`;
    } else {
      return 'Select Process';
    }
  };

  isProcDefSelected = () => {
    return this.props.processDefinitionKey && this.props.processDefinitionVersion;
  };

  renderInput = ({type, label, bpmnKey}) => {
    const {hoveredControl, hoveredNode} = this.props;
    const disableFlowNodeSelection = !this.isProcDefSelected();

    return (
      <div
        className={classnames('AnalysisControlPanel__config', {
          'AnalysisControlPanel__config--hover':
            !disableFlowNodeSelection &&
            (hoveredControl === type || (hoveredNode && hoveredNode.$instanceOf(bpmnKey)))
        })}
        name={'AnalysisControlPanel__' + type}
        onMouseOver={this.hover(type)}
        onMouseOut={this.hover(null)}
      >
        <ActionItem
          disabled={disableFlowNodeSelection}
          onClick={() => this.props.updateSelection(type, null)}
        >
          {this.props[type]
            ? this.props[type].name || this.props[type].id
            : 'Please Select ' + label}
        </ActionItem>
      </div>
    );
  };

  render() {
    return (
      <div className="AnalysisControlPanel">
        <ul className="AnalysisControlPanel__list">
          <li className="AnalysisControlPanel__item summary">
            For
            <Popover className="AnalysisControlPanel__popover" title={this.createTitle()}>
              <ProcessDefinitionSelection
                {...this.getDefinitionConfig()}
                xml={this.props.xml}
                onChange={(key, version) =>
                  this.props.onChange({
                    processDefinitionKey: key,
                    processDefinitionVersion: version
                  })
                }
              />
            </Popover>
            analyse how the branches of
            {this.renderInput({type: 'gateway', label: 'Gateway', bpmnKey: 'bpmn:Gateway'})}
            affect the probability that an instance reached
            {this.renderInput({type: 'endEvent', label: 'End Event', bpmnKey: 'bpmn:EndEvent'})}
          </li>
          <li className="AnalysisControlPanel__item AnalysisControlPanel__item--filter">
            <Filter
              data={this.props.filter}
              flowNodeNames={this.state.flowNodeNames}
              onChange={this.props.onChange}
              xml={this.props.xml}
              {...this.getDefinitionConfig()}
            />
          </li>
        </ul>
      </div>
    );
  }
}
