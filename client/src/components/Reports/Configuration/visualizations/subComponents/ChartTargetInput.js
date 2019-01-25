import React from 'react';

import {ButtonGroup, Button, Input, ErrorMessage, Select} from 'components';
import classnames from 'classnames';
import './ChartTargetInput.scss';
import {numberParser} from 'services';

export default class ChartTargetInput extends React.Component {
  state = {
    value: '0'
  };

  componentDidMount() {
    this.setState({value: this.props.configuration.targetValue[this.getType()].value});
  }

  getType = () => {
    const {report} = this.props;

    const referenceReport = report.combined ? Object.values(report.result)[0] : report;
    return referenceReport.data.view.operation === 'count' ? 'countChart' : 'durationChart';
  };

  setValue = (prop, value) => {
    if (prop === 'value') {
      this.setState({value});

      if (!numberParser.isNonNegativeNumber(value)) {
        return;
      }

      value = +value;
    }

    this.props.onChange({
      targetValue: {
        [this.getType()]: {
          [prop]: {$set: value}
        }
      }
    });
  };

  render() {
    const {configuration: {targetValue}} = this.props;

    const type = this.getType();

    const isInvalid = !numberParser.isNonNegativeNumber(this.state.value);

    return (
      <div className="ChartTargetInput">
        <ButtonGroup className="buttonGroup" disabled={!targetValue.active}>
          <Button
            onClick={() => this.setValue('isBelow', false)}
            className={classnames({'is-active': !targetValue[type].isBelow})}
          >
            Above
          </Button>
          <Button
            onClick={() => this.setValue('isBelow', true)}
            className={classnames({'is-active': targetValue[type].isBelow})}
          >
            Below
          </Button>
        </ButtonGroup>
        <Input
          className="targetInput"
          type="number"
          placeholder="Goal value"
          value={this.state.value}
          onChange={({target: {value}}) => this.setValue('value', value)}
          isInvalid={isInvalid}
          disabled={!targetValue.active}
        />
        {isInvalid && (
          <ErrorMessage className="InvalidTargetError">Must be a non-negative number</ErrorMessage>
        )}
        {type === 'durationChart' && (
          <Select
            className="dataUnitSelect"
            value={targetValue[type].unit}
            onChange={({target: {value}}) => this.setValue('unit', value)}
            disabled={!targetValue.active}
          >
            <Select.Option value="millis">Milliseconds</Select.Option>
            <Select.Option value="seconds">Seconds</Select.Option>
            <Select.Option value="minutes">Minutes</Select.Option>
            <Select.Option value="hours">Hours</Select.Option>
            <Select.Option value="days">Days</Select.Option>
            <Select.Option value="weeks">Weeks</Select.Option>
            <Select.Option value="months">Months</Select.Option>
            <Select.Option value="years">Years</Select.Option>
          </Select>
        )}
      </div>
    );
  }
}
