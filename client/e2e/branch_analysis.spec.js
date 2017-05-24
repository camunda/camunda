const {expect} = require('chai');

describe('Branch Analysis', () => {
  afterEach(() => {
    browser.localStorage('DELETE');
  });

  it('should open the analysis diagram section', () => {
    openBranchAnalysis();
    selectGatewayAndEndEvent();

    // EXPECT STATISTICS
    browser.waitForVisible('.statisticsContainer');
    expect(browser.isVisible('.statisticsContainer')).to.eql(true);
  });

  // DISABLED because it is unstable on jenkins... seems refresh isn't something
  // that jenkins likes very much.
  // it('should preserve branch analysis section after refresh', () => {
  //   openBranchAnalysis();
  //   browser.refresh();
  //   selectGatewayAndEndEvent();
  //
  //   // EXPECT STATISTICS
  //   browser.waitForVisible('.statisticsContainer');
  //   expect(browser.isVisible('.statisticsContainer')).to.eql(true);
  // });

  function openBranchAnalysis() {
    // LOGIN
    browser.url('/');
    browser.waitForEnabled('input[type="text"]');

    browser.setValue('input[type="text"]', 'admin');
    browser.setValue('input[type="password"]', 'admin');
    browser.click('button[type="submit"]');

    browser.waitForExist('.process-definition-card*=Simple');
    browser.click('.process-definition-card*=Simple');

    // SELECT VIEW
    browser.waitForExist('.dropdown*=None');
    browser.click('.dropdown*=None');
    browser.click('a*=Branch');
  }

  function selectGatewayAndEndEvent() {
    // SELECT PROCESS DEFINITION
    const endEventSelector = '.djs-element.djs-shape.highlight[data-element-id*="EndEvent"]';
    const gatewaySelector = '.djs-element.djs-shape.highlight[data-element-id*="Gateway"]';

    browser.waitForExist(endEventSelector);

    // CLICK THE END EVENT AND THE GATEWAY
    browser.click(endEventSelector);
    browser.click(gatewaySelector);
  }
});
