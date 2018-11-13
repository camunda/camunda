import React from 'react';

import TargetValueModal from './TargetValueModal';

import {isDurationHeatmap, isChart} from './service';

import {ButtonGroup, Button, Icon} from 'components';

import './TargetValueComparison.scss';

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
    return isDurationHeatmap(this.props.reportResult) || isChart(this.props.reportResult);
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
          <Icon type="settings" />
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
