const {expect} = require('chai');

describe('Login Page', () => {
  afterEach(() => {
    browser.localStorage('DELETE');
  });

  it('should be able to login', () => {
    browser.url('/');
    browser.waitForEnabled('input[type="text"]');

    browser.setValue('input[type="text"]', 'admin');
    browser.setValue('input[type="password"]', 'admin');
    browser.click('button[type="submit"]');

    browser.waitForEnabled('*=Logout');

    expect(browser.isExisting('form.form-signin')).to.eql(false);
    expect(browser.isVisible('*=Logout')).to.eql(true);
  });

  it('should be able to login after refresh', () => {
    browser.url('/');
    browser.waitForEnabled('input[type="text"]');

    browser.setValue('input[type="text"]', 'admin');
    browser.setValue('input[type="password"]', 'admin');
    browser.click('button[type="submit"]');

    browser.waitForEnabled('*=Logout');

    browser.refresh();

    expect(browser.isExisting('form.form-signin')).to.eql(false);
    expect(browser.isVisible('*=Logout')).to.eql(true);
  });
});
