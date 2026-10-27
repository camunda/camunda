/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';

import InfoMenu from './InfoMenu';

jest.mock('translation', () => ({
  t: jest.fn().mockImplementation((key) => key),
}));

function getItemLinks(node: ReturnType<typeof shallow>): Record<string, string> {
  const open = jest.fn();
  jest.spyOn(window, 'open').mockImplementation(open);

  return node.find('DropdownMenuItem').reduce<Record<string, string>>((links, item) => {
    open.mockClear();
    item.simulate('click');
    links[item.key()] = open.mock.calls[0]?.[0];
    return links;
  }, {});
}

it('should link to the documentation of the running version', () => {
  const node = shallow(<InfoMenu docsUrl="https://docs.camunda.io/8.9/" enterpriseMode={false} />);

  expect(getItemLinks(node).documentation).toBe('https://docs.camunda.io/8.9/');
});

it('should send community users to the forum for support', () => {
  const node = shallow(<InfoMenu docsUrl="docsUrl" enterpriseMode={false} />);

  expect(getItemLinks(node).feedbackAndSupport).toBe('https://forum.camunda.io/');
});

it('should send enterprise users to the support desk', () => {
  const node = shallow(<InfoMenu docsUrl="docsUrl" enterpriseMode />);

  expect(getItemLinks(node).feedbackAndSupport).toBe(
    'https://jira.camunda.com/projects/SUPPORT/queues'
  );
});
