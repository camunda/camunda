/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';

import {Transition as TransitionComponent} from 'modules/components/Transition';

import Table from 'modules/components/Table';

const FirstCell = styled.div`
  position: relative;
  padding-left: 23px;
  z-index: 0;
`;

const Index = styled.span`
  ${({theme}) => {
    return css`
      position: absolute;
      top: 0;
      left: -54px;
      width: 35px;
      text-align: right;
      font-size: 11px;
      opacity: 0.6;
      color: ${theme.colors.text02};
    `;
  }}
`;

const ErrorMessageCell = styled.div`
  margin-right: 10px;
  width: 404px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
`;

const Flex = styled.div`
  display: flex;
  align-items: center;
`;

const FirstTH = styled(Table.TH)`
  ${({theme}) => {
    const colors = theme.colors.incidentsTable.firstTh;

    return css`
      position: relative;
      height: 100%;
      padding-left: 27px;
      z-index: 0;

      &:before {
        content: ' ';
        float: left;
        height: 31px;
        margin-top: 3px;
        margin-right: 5px;
        width: 1px;
        background: ${colors.before.backgroundColor};
      }

      &:after {
        content: ' ';
        float: right;
        height: 31px;
        margin-top: 3px;
        width: 1px;
        background: ${colors.after.backgroundColor};
      }
    `;
  }}
`;

const TH = styled(Table.TH)`
  ${({theme}) => {
    const colors = theme.colors.incidentsTable.firstTh;

    return css`
      &:not(:last-child):after {
        content: ' ';
        float: right;
        height: 31px;
        margin-top: 3px;
        width: 1px;
        background: ${colors.after.backgroundColor};
      }
    `;
  }}
`;

const IncidentTR = styled(Table.TR)`
  ${({theme, isSelected}) => {
    const colors = theme.colors.incidentsTable.incidentTr;
    const opacity = theme.opacity.incidentsTable.incidentTr;

    return css`
      cursor: pointer;
      transition: background-color 200ms, opacity 200ms;
      opacity: ${isSelected ? opacity.selected : opacity.default};

      &:nth-child(odd) {
        background-color: ${isSelected
          ? theme.colors.selectedOdd
          : theme.colors.itemOdd};
      }
      &:nth-child(even) {
        background-color: ${isSelected
          ? theme.colors.selectedEven
          : theme.colors.itemEven};
      }

      &:hover {
        transition: background-color 150ms ease-out;
        ${isSelected
          ? ''
          : css`
              background-color: ${colors.hover.backgroundColor};
            `};
      }
    `;
  }}
`;

const Fake = styled.span`
  ${({theme}) => {
    const colors = theme.colors.incidentsTable.fake;

    return css`
      background: ${colors.backgroundColor};
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
        border-bottom: 1px solid ${theme.colors.borderColor};
        border-top: 1px solid ${theme.colors.borderColor};
        background-color: ${colors.before.backgroundColor};
      }
    `;
  }}
`;

type TransitionProps = {
  timeout: {
    enter: number;
    exit: number;
  };
};

// @ts-expect-error ts-migrate(2769) FIXME: Type 'undefined' is not assignable to type 'ReactE... Remove this comment to see the full error message
const Transition = styled(TransitionComponent)<TransitionProps>`
  ${({timeout}) => {
    return css`
      &.transition-enter > td > div {
        opacity: 0.25;
      }
      &.transition-enter-active > td > div {
        opacity: 1;
        transition: opacity ${timeout.enter}ms;
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
        transition: all ${timeout.exit}ms;
      }

      &.transition-exit-done {
        border-width: 0px;
        background-color: transparent;
      }
    `;
  }}
`;

export {
  FirstCell,
  Index,
  ErrorMessageCell,
  Flex,
  FirstTH,
  IncidentTR,
  Fake,
  Transition,
  TH,
};
