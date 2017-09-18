import {jsx} from 'view-utils';
import {mountTemplate, createMockComponent} from 'testHelpers';
import {expect} from 'chai';
import sinon from 'sinon';
import {StatisticChart, __set__, __ResetDependency__} from 'main/processDisplay/views/analytics/statistics/StatisticChart';

describe('<StatisticChart>', () => {
  let node;
  let update;
  let Chart;
  let isLoading;
  let data;

  beforeEach(() => {
    Chart = createMockComponent('Chart');
    __set__('Chart', Chart);

    data = sinon.stub().returns([]);
  });

  afterEach(() => {
    __ResetDependency__('Chart');
  });

  describe('loading', () => {
    beforeEach(() => {
      isLoading = sinon.stub().returns(true);

      ({node, update} = mountTemplate(<StatisticChart
        isLoading={isLoading}
        data={data}
        chartConfig={{}}
      >some header text</StatisticChart>));
      update({});
    });

    it('should display a loading indicator if data is loading', () => {
      expect(node.querySelector('.loading_indicator')).to.exist;
      expect(node.querySelector('.chart')).to.not.exist;
    });
  });

  describe('loaded', () => {
    beforeEach(() => {
      isLoading = sinon.stub().returns(false);

      ({node, update} = mountTemplate(<StatisticChart
        isLoading={isLoading}
        data={data}
        chartConfig={{}}
      >some header text</StatisticChart>));
      update({});
    });

    it('should contain a header', () => {
      expect(node.querySelector('.chart-header')).to.exist;
    });

    it('should contain the child content in the header text', () => {
      expect(node.querySelector('.chart-header').textContent).to.eql('some header text');
    });

    it('should display a chart', () => {
      expect(node.querySelector('.chart')).to.exist;
    });

    it('should show a no data indicator if there is no data', () => {
      expect(node.querySelector('.no-data-indicator')).to.exist;
    });
  });
});
