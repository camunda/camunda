/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {runAllEffects} from '__mocks__/react';
import {shallow} from 'enzyme';

import {get} from 'request';
import {User} from 'HOC';

import {DownloadButton, DownloadButtonProps} from './DownloadButton';

HTMLAnchorElement.prototype.click = jest.fn();

jest.mock('request', () => ({get: jest.fn().mockReturnValue({blob: jest.fn()})}));

jest.mock('config', () => ({
  getExportCsvLimit: jest.fn().mockReturnValue(3),
}));

jest.mock('hooks', () => ({
  useErrorHandling: jest.fn(() => ({
    mightFail: jest.fn((data, cb) => cb(data)),
  })),
  useDocs: jest.fn(() => ({
    generateDocsLink: () => '',
  })),
}));

const props: DownloadButtonProps = {
  href: '',
  totalCount: 0,
  user: {authorizations: ['csv_export']} as unknown as User,
};

beforeAll(() => {
  window.URL.createObjectURL = jest.fn();
});

beforeEach(() => {
  jest.clearAllMocks();
});

it('invoke get with the provided href', () => {
  const node = shallow(<DownloadButton {...props} href="testUrl" />);

  node.find('Button').first().simulate('click');

  expect(get).toHaveBeenCalledWith('testUrl');
});

it('invoke the retriever function when provided', () => {
  const retriever = jest.fn();
  const spy = jest.fn();
  const {href, ...restOfProps} = props;
  const node = shallow(
    <DownloadButton retriever={retriever} {...restOfProps} fileName="testName" mightFail={spy} />
  );

  node.find('Button').first().simulate('click');

  expect(retriever).toHaveBeenCalled();
});

it('should display a modal if total download count is more than csv limit', async () => {
  const node = shallow(<DownloadButton fileName="testName" {...props} totalCount={5} />);

  await runAllEffects();

  node.find('Button').first().simulate('click');

  expect(node.find('Modal').prop('open')).toBe(true);
});

it('should not display the button if the user is not authorized to export csv data', () => {
  const node = shallow(
    <DownloadButton fileName="testName" {...props} user={{authorizations: []} as unknown as User} />
  );

  runAllEffects();

  expect(node.find('Button')).not.toExist();
});

it('should disable the button during downloading', () => {
  const slowRetriever = (): Promise<Blob> =>
    new Promise((resolve) =>
      setTimeout(() => {
        resolve(new Blob());
      }, 1000)
    );
  const node = shallow(
    <DownloadButton fileName="testName" {...props} retriever={slowRetriever} href={undefined} />
  );

  node.find('Button').first().simulate('click');

  expect(node.find('Button').first().prop('disabled')).toBe(true);
});
