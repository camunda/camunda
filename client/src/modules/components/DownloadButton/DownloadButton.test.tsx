/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {runAllEffects} from '__mocks__/react';
import {shallow} from 'enzyme';

import {Button} from 'components';
import {get} from 'request';

import {DownloadButton, DownloadButtonProps} from './DownloadButton';
import {User} from 'HOC';

HTMLAnchorElement.prototype.click = jest.fn();

jest.mock('request', () => ({get: jest.fn().mockReturnValue({blob: jest.fn()})}));

jest.mock('config', () => ({
  getExportCsvLimit: jest.fn().mockReturnValue(3),
}));

const props: DownloadButtonProps = {
  href: '',
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
  user: {authorizations: ['csv_export']} as unknown as User,
  totalCount: 0,
  getUser: () =>
    Promise.resolve({
      authorizations: ['csv_export'],
    } as unknown as User),
  refreshUser: () =>
    Promise.resolve({
      authorizations: ['csv_export'],
    } as User),
  docsLink: '',
};

beforeAll(() => {
  window.URL.createObjectURL = jest.fn();
});

it('invoke get with the provided href', async () => {
  const node = shallow(<DownloadButton {...props} href="testUrl" />);

  node.find(Button).first().simulate('click');

  await flushPromises();

  expect(get).toHaveBeenCalledWith('testUrl');
});

it('invoke the retriever function when provided', () => {
  const retriever = jest.fn();
  const spy = jest.fn();
  const {href, ...restOfProps} = props;
  const node = shallow(
    <DownloadButton retriever={retriever} {...restOfProps} fileName="testName" mightFail={spy} />
  );

  node.find(Button).first().simulate('click');

  expect(retriever).toHaveBeenCalled();
});

it('should display a modal if total download count is more than csv limit', async () => {
  const node = shallow(<DownloadButton fileName="testName" {...props} totalCount={5} />);

  await runAllEffects();

  node.find(Button).first().simulate('click');

  expect(node.find('Modal').prop('open')).toBe(true);
});

it('should not display the button if the user is not authorized to export csv data', async () => {
  const node = shallow(
    <DownloadButton fileName="testName" {...props} user={{authorizations: []} as unknown as User} />
  );

  await runAllEffects();

  expect(node.find(Button)).not.toExist();
});
