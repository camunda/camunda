import React from 'react';
import {mount} from 'enzyme';

import ExternalReport from './ExternalReport';

it('should include an iframe with the provided external url', () => {
  const node = mount(<ExternalReport report={{configuration: {external: 'externalURL'}}} />);

  const iframe = node.find('iframe');

  expect(iframe).toBePresent();
  expect(iframe).toHaveProp('src', 'externalURL');
});
