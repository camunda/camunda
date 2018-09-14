import React from 'react';

import TargetValueModal from './TargetValueModal';

import {isSingleNumber, isDurationHeatmap, isChart} from './service';

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

  isSingleNumber = () => {
    const {
      processDefinitionKey,
      processDefinitionVersion,
      visualization
    } = this.props.reportResult.data;

    return processDefinitionKey && processDefinitionVersion && visualization === 'number';
  };

  isEnabled = () => {
    return (
      isDurationHeatmap(this.props.reportResult.data) ||
      isSingleNumber(this.props.reportResult.data) ||
      isChart(this.props.reportResult.data)
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
