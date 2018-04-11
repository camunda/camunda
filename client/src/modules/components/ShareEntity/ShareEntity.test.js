import React from 'react';
import {mount} from 'enzyme';

import ShareEntity from './ShareEntity';

jest.mock('components', () => {
  return {
    CopyToClipboard: props => (
      <div id="copy" value={props.value}>
        {props.value}
      </div>
    ),
    Switch: props => <input id="switch" ref={props.ref} value={props.value} {...props} />,
    Icon: props => <span>{props.type}</span>
  };
});

const props = {
  shareEntity: jest.fn(),
  revokeEntitySharing: jest.fn(),
  getSharedEntity: jest.fn()
};

it('should render without crashing', () => {
  mount(<ShareEntity {...props} />);
});

it('should initially get already shared entities', () => {
  mount(<ShareEntity {...props} />);

  expect(props.getSharedEntity).toHaveBeenCalled();
});

it('should share entity if is checked', () => {
  props.getSharedEntity.mockReturnValue(10);

  const node = mount(<ShareEntity {...props} />);

  node.instance().toggleValue({target: {checked: true}});

  expect(props.shareEntity).toHaveBeenCalled();
});

it('should delete entity if sharing is revoked', () => {
  props.getSharedEntity.mockReturnValue(10);

  const node = mount(<ShareEntity {...props} />);

  node.instance().toggleValue({target: {checked: false}});

  expect(props.revokeEntitySharing).toHaveBeenCalled();
});

it('should construct special link', () => {
  const node = mount(<ShareEntity type="report" {...props} />);
  Object.defineProperty(window.location, 'origin', {
    value: 'http://example.com'
  });

  node.setState({loaded: true, id: 10});

  expect(node.find('.ShareEntity__share-link')).toIncludeText(`http://example.com/share/report/10`);
});

it('should construct special link for embedding', () => {
  const node = mount(<ShareEntity type="report" {...props} />);
  Object.defineProperty(window.location, 'origin', {
    value: 'http://example.com'
  });

  node.setState({loaded: true, id: 10});

  expect(node.find('.ShareEntity__embed-link')).toIncludeText(
    `<iframe src="http://example.com/share/report/10`
  );
});

it('should display a loading indicator', () => {
  const node = mount(<ShareEntity {...props} />);

  expect(node.find('.ShareEntity__loading-indicator')).toBePresent();
});
