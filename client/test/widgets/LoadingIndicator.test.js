import {jsx} from 'view-utils';
import {mountTemplate} from 'testHelpers';
import {expect} from 'chai';
import {LoadingIndicator} from 'widgets/LoadingIndicator';

describe('<LoadingIndicator>', () => {
  let update;
  let node;

  describe('loading', () => {
    beforeEach(() => {
      const loading = () => true;

      ({update, node} = mountTemplate(
        <LoadingIndicator predicate={loading}>
        some content
        </LoadingIndicator>
      ));

      update({});
    });

    it('should display a loading indicator', () => {
      expect(node.querySelector('.loading_indicator')).to.exist;
    });
  });

  describe('loaded', () => {
    beforeEach(() => {
      const loading = () => false;

      ({update, node} = mountTemplate(
        <LoadingIndicator predicate={loading}>
        some content
        </LoadingIndicator>
      ));

      update({});
    });

    it('should display child content', () => {
      expect(node.textContent).to.eql('some content');
    });
  });
});
