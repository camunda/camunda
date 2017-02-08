import {expect} from 'chai';
import {mountTemplate} from 'testHelpers/mountTemplate';
import {jsx} from 'view-utils';
import {Header} from 'main/header';

describe('<Header>', () => {
  let node;

  beforeEach(() => {
    ({node} = mountTemplate(<Header/>));
  });

  it('should contain header text', () => {
    expect(node).to.contain.text('Camunda Optimize');
  });
});
