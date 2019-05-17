/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import MetricPanel from './MetricPanel';
import * as Styled from './styled.js';

describe('MetricPanel', () => {
  describe('Title', () => {
    it('should render title containing instances count', () => {
      // given
      const props = {runningInstancesCount: 12, incidentsCount: 11};

      // when
      const node = shallow(<MetricPanel {...props} />);
      const titleNode = node.find(Styled.Title);

      // then
      const expectedCount = props.runningInstancesCount + props.incidentsCount;

      expect(titleNode).toExist();
      expect(titleNode.text()).toEqual(
        `${expectedCount} Running Instances in total`
      );
    });

    it('should render correct link (if no instances)', () => {
      // given
      const props = {runningInstancesCount: 12, incidentsCount: 11};

      // when
      const node = shallow(<MetricPanel {...props} />);
      const titleNode = node.find(Styled.Title);

      // then
      expect(titleNode.props().to).toEqual(
        '/instances?filter={"active":true,"incidents":true}'
      );
    });

    it('should render correct link (if instances)', () => {
      // given
      const props = {runningInstancesCount: 0, incidentsCount: 0};

      // when
      const node = shallow(<MetricPanel {...props} />);
      const titleNode = node.find(Styled.Title);

      // then
      expect(titleNode.props().to).toEqual(
        '/instances?filter={"active":true,"incidents":true,"completed":true,"canceled":true}'
      );
    });
  });

  describe('Label', () => {
    it('should pass correct link to incident label (if incidents)', () => {
      // given
      const props = {runningInstancesCount: 5, incidentsCount: 5};

      // when
      const node = shallow(<MetricPanel {...props} />);
      const IncidentsLabelNode = node.find(Styled.Label).at(0);

      // then
      expect(IncidentsLabelNode.props().to).toEqual(
        '/instances?filter={"incidents":true}'
      );
    });

    it('should pass correct link to incident label (if no incidents)', () => {
      // given
      const props = {runningInstancesCount: 5, incidentsCount: 0};

      // when
      const node = shallow(<MetricPanel {...props} />);
      const IncidentsLabelNode = node.find(Styled.Label).at(0);

      // then
      expect(IncidentsLabelNode.props().to).toEqual(
        '/instances?filter={"incidents":true}'
      );
    });

    it('should pass correct link to active instances label (if instances)', () => {
      // given
      const props = {runningInstancesCount: 5, incidentsCount: 5};

      // when
      const node = shallow(<MetricPanel {...props} />);
      const ActiveInstancesLabelNode = node.find(Styled.Label).at(1);

      // then
      expect(ActiveInstancesLabelNode.props().to).toEqual(
        '/instances?filter={"active":true}'
      );
    });

    it('should pass correct link to active instances label (if no instances)', () => {
      // given
      const props = {runningInstancesCount: 0, incidentsCount: 5};

      // when
      const node = shallow(<MetricPanel {...props} />);
      const ActiveInstancesLabelNode = node.find(Styled.Label).at(1);

      // then
      expect(ActiveInstancesLabelNode.props().to).toEqual(
        '/instances?filter={"active":true}'
      );
    });
  });

  it('should pass panel data to InstancesBar', () => {
    const props = {runningInstancesCount: 4, incidentsCount: 2};

    const node = shallow(<MetricPanel {...props} />);

    const InstancesBarNode = node.find(Styled.InstancesBar);

    expect(InstancesBarNode).toExist();

    const InstancesBarProps = InstancesBarNode.props();

    expect(InstancesBarProps.activeCount).toEqual(props.runningInstancesCount);
    expect(InstancesBarProps.incidentsCount).toEqual(props.incidentsCount);
    expect(InstancesBarProps.incidentsCount).toEqual(props.incidentsCount);
  });
});
