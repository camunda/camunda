/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ChangeEventHandler, Component} from 'react';
import {Button, Form, Stack, TextInput} from '@carbon/react';

import {Modal, Select} from 'components';
import {numberParser} from 'services';
import {Definition, FilterData} from 'types';
import {t} from 'translation';

import FilterDefinitionSelection from '../FilterDefinitionSelection';
import {FilterProps} from '../types';

interface DurationFilterState extends FilterData {
  applyTo: Definition[];
}

export default class DurationFilter extends Component<
  FilterProps<'processInstanceDuration'>,
  DurationFilterState
> {
  constructor(props: FilterProps<'processInstanceDuration'>) {
    super(props);

    let applyTo: Definition[] = [
      {
        identifier: 'all',
        displayName: t('common.filter.definitionSelection.allProcesses'),
      },
    ];

    if (props.filterData && props.filterData.appliedTo?.[0] !== 'all') {
      applyTo = props.filterData.appliedTo
        .map((id) => props.definitions.find(({identifier}) => identifier === id))
        .filter((definition): definition is Definition => !!definition);
    }

    this.state = {
      value: props.filterData?.data.value.toString() ?? '7',
      operator: props.filterData?.data.operator ?? '>',
      unit: props.filterData?.data.unit ?? 'days',
      applyTo,
    };
  }

  createFilter = () => {
    const {value, operator, unit, applyTo} = this.state;

    this.props.addFilter({
      type: 'processInstanceDuration',
      data: {
        value: parseFloat(value.toString()),
        operator,
        unit,
      },
      appliedTo: applyTo.map(({identifier}) => identifier),
    });
  };

  render() {
    const {value, operator, unit, applyTo} = this.state;
    const {definitions} = this.props;

    const isValidInput = numberParser.isPositiveInt(value);
    const isValidFilter = isValidInput && applyTo.length > 0;

    return (
      <Modal size="sm" open onClose={this.props.close} className="DurationFilter" isOverflowVisible>
        <Modal.Header
          title={t('common.filter.modalHeader', {
            type: t('common.filter.types.processInstanceDuration').toString(),
          })}
        />
        <Modal.Content>
          <FilterDefinitionSelection
            availableDefinitions={definitions}
            applyTo={applyTo}
            setApplyTo={(applyTo) => this.setState({applyTo})}
          />
          <Stack gap={4}>
            <p className="description">{t('common.filter.durationModal.includeInstance')} </p>
            <Form>
              <Stack gap={4} orientation="horizontal">
                <Select
                  size="md"
                  id="more-less-selector"
                  value={operator}
                  onChange={this.setOperator}
                >
                  <Select.Option value=">" label={t('common.filter.durationModal.moreThan')} />
                  <Select.Option value="<" label={t('common.filter.durationModal.lessThan')} />
                </Select>
                <TextInput
                  size="md"
                  labelText={t('common.value')}
                  hideLabel
                  id="duration-value-input"
                  invalid={!isValidInput}
                  value={value}
                  onChange={this.setValue}
                  maxLength={8}
                  invalidText={t('common.errors.positiveInt')}
                />
                <Select size="md" id="duration-units-selector" value={unit} onChange={this.setUnit}>
                  <Select.Option value="millis" label={t('common.unit.milli.label-plural')} />
                  <Select.Option value="seconds" label={t('common.unit.second.label-plural')} />
                  <Select.Option value="minutes" label={t('common.unit.minute.label-plural')} />
                  <Select.Option value="hours" label={t('common.unit.hour.label-plural')} />
                  <Select.Option value="days" label={t('common.unit.day.label-plural')} />
                  <Select.Option value="weeks" label={t('common.unit.week.label-plural')} />
                  <Select.Option value="months" label={t('common.unit.month.label-plural')} />
                  <Select.Option value="years" label={t('common.unit.year.label-plural')} />
                </Select>
              </Stack>
            </Form>
          </Stack>
        </Modal.Content>
        <Modal.Footer>
          <Button kind="secondary" className="cancel" onClick={this.props.close}>
            {t('common.cancel')}
          </Button>
          <Button className="confirm" disabled={!isValidFilter} onClick={this.createFilter}>
            {this.props.filterData ? t('common.filter.updateFilter') : t('common.filter.addFilter')}
          </Button>
        </Modal.Footer>
      </Modal>
    );
  }

  setOperator = (operator: string) => this.setState({operator});
  setUnit = (unit: string) => this.setState({unit});
  setValue: ChangeEventHandler<HTMLInputElement> = ({target: {value}}) => this.setState({value});
}
