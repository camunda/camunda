/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';
import {Transition as TransitionComponent} from 'modules/components/Transition';

import Table from 'modules/components/Table';
const {TH, TR} = Table;

export const FirstCell = styled.div`
  position: relative;
  padding-left: 23px;
  z-index: 0;
`;

export const Index = styled.span`
  position: absolute;
  top: 0;
  left: -54px;
  width: 35px;

  text-align: right;

  font-size: 11px;
  opacity: 0.6;
  color: ${themeStyle({
    dark: Colors.white,
    light: Colors.uiLight06
  })};
`;

export const ErrorMessageCell = styled.div`
  margin-right: 10px;
  width: 404px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
`;

export const Flex = styled.div`
  display: flex;
  align-items: center;
`;

export const FirstTH = themed(styled(TH)`
  position: relative;
  height: 100%;
  padding-left: 27px
  z-index: 0;

  &:before {
    content: ' ';
    float: left;
    height: 31px;
    margin-top: 3px;
    margin-right: 5px;
    width: 1px;
    background: ${themeStyle({
      dark: Colors.uiDark04,
      light: Colors.uiLight05
    })};
  }

  &:after {
    content: ' ';
    float: right;
    height: 31px;
    margin-top: 3px;
    width: 1px;
    background: ${themeStyle({
      dark: Colors.uiDark04,
      light: Colors.uiLight05
    })};
  }
`);

export const IncidentTR = themed(styled(TR)`
  cursor: pointer;

  &:nth-child(odd) {
    background-color: ${themeStyle({
      dark: ({isSelected}) => {
        return isSelected ? Colors.darkInstanceOdd : Colors.darkItemOd;
      },
      light: ({isSelected}) => {
        return isSelected ? Colors.lightInstanceOdd : Colors.lightItemOdd;
      }
    })};
  }
  &:nth-child(even) {
    background-color: ${themeStyle({
      dark: ({isSelected}) => {
        return isSelected ? Colors.darkInstanceEven : Colors.darkItemEven;
      },
      light: ({isSelected}) => {
        return isSelected ? Colors.lightInstanceEven : Colors.lightItemEven;
      }
    })};
  }

  &:hover {
    background-color: ${({isSelected}) => {
      return (
        !isSelected &&
        themeStyle({
          dark: Colors.darkTreeHover,
          light: Colors.lightButton05
        })
      );
    }};
  }
`);

export const Fake = themed(styled.span`
  background: yellow;
  position: absolute;

  top: 0;
  left: 0;
  height: 100%;
  width: 0;

  &:before {
    content: '';
    position: absolute;
    top: -1px;
    left: -52px;
    width: 52px;
    height: 100%;

    border-bottom: 1px solid
      ${themeStyle({
        dark: Colors.uiDark04,
        light: Colors.uiLight05
      })};
    border-top: 1px solid
      ${themeStyle({
        dark: Colors.uiDark04,
        light: Colors.uiLight05
      })};

    background-color: ${themeStyle({
      dark: Colors.uiDark03,
      light: Colors.uiLight02
    })};
  }
`);

export const Transition = themed(styled(TransitionComponent)`
  &.transition-enter {
    opacity: 0.25;
  }
  &.transition-enter-active {
    opacity: 1;
    transition: opacity ${({timeout}) => timeout.enter + 'ms'};
  }

  &.transition-exit > td > div {
    height: 0px;
    overflow: hidden;
    border-width: 0px;
    background-color: transparent;
  }

  &.transition-exit-active {
    height: 0px;
    overflow: hidden;
    background-color: transparent;
    border-width: 0px;
    transition: all ${({timeout}) => timeout.exit + 'ms'};
  }

  &.transition-exit-done {
    border-width: 0px;
    background-color: transparent;
  }
`);
