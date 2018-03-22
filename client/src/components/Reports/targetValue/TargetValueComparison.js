import React from 'react';

import TargetValueModal from './TargetValueModal';

import {ButtonGroup, Button} from 'components';
import settingsIcon from './settings.svg';

import './TargetValueComparison.css';

export default class TargetValueComparison extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      modalOpen: false
    };
  }

  getConfig = () => {
    return this.props.configuration.targetValue || {};
  };

  toggleMode = () => {
    const {active, values} = this.getConfig();

    if (active) {
      this.setActive(false);
    } else if (!values || Object.keys(values).length === 0) {
      this.openModal();
    } else {
      this.setActive(true);
    }
  };

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
  };

  openModal = async () => {
    this.setState({
      modalOpen: true
    });
  };

  closeModal = () => {
    this.setState({
      modalOpen: false
    });
  };

  confirmModal = values => {
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
  };

  isEnabled = () => {
    const {
      processDefinitionKey,
      processDefinitionVersion,
      view,
      groupBy,
      visualization
    } = this.props.reportResult.data;

    return (
      processDefinitionKey &&
      processDefinitionVersion &&
      view.entity === 'flowNode' &&
      view.operation === 'avg' &&
      view.property === 'duration' &&
      groupBy.type === 'flowNode' &&
      visualization === 'heat'
    );
  };

  render() {
    const isEnabled = this.isEnabled();

    return (
      <ButtonGroup className="TargetValueComparison">
        <Button
          className="TargetValueComparison__toggleButton"
          disabled={!isEnabled}
          active={this.getConfig().active}
          onClick={this.toggleMode}
        >
          Target Value
        </Button>
        <Button
          className="TargetValueComparison__editButton"
          disabled={!isEnabled}
          onClick={this.openModal}
        >
          <img src={settingsIcon} alt="settings" className="TargetValueComparison__settingsIcon" />
        </Button>
        <TargetValueModal
          open={this.state.modalOpen}
          onClose={this.closeModal}
          configuration={this.props.configuration}
          onConfirm={this.confirmModal}
          reportResult={this.props.reportResult}
        />
      </ButtonGroup>
    );
  }
}
