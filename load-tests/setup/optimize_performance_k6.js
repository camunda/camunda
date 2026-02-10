import http from "k6/http";
import exec from 'k6/execution';
import { browser } from "k6/browser";
import { sleep, check, fail } from 'k6';
import { Trend } from 'k6/metrics';

// Environment variables
const BASE_URL = __ENV.BASE_URL || "http://localhost:8083";
const USERNAME = __ENV.USERNAME || "demo";
const PASSWORD = __ENV.PASSWORD || "zuSi25meGPQSri4R";

// Custom metrics
const homepageLoadTime = new Trend('optimize_homepage_load_time', true);
const loginDuration = new Trend('optimize_login_duration', true);
const totalFlowDuration = new Trend('optimize_total_flow_duration', true);

export const options = {
  scenarios: {
    ui: {
      executor: "shared-iterations",
      vus: 1,
      iterations: 1,
      options: {
        browser: {
          type: "chromium",
        },
      },
    },
  },
  thresholds: {
    'optimize_homepage_load_time': ['p(95)<5000'], // 95% of homepage loads should be under 5s
    'optimize_login_duration': ['p(95)<3000'],      // 95% of logins should be under 3s
  },
};

export function setup() {
  if (!PASSWORD) {
    exec.test.abort('PASSWORD environment variable is required');
  }
  // Check if the service is reachable
  // Note: Optimize redirects to Keycloak (302), so we accept 200, 302, or 3xx status codes
  let res = http.get(BASE_URL, { redirects: 0 });
  if (res.status !== 200 && res.status !== 302 && (res.status < 300 || res.status >= 400)) {
    exec.test.abort(`Got unexpected status code ${res.status} when trying to setup. Exiting.`);
  }
  console.log(`✓ Service is reachable at ${BASE_URL} (status: ${res.status})`);
}

/**
 * Navigate to Optimize and handle Keycloak redirect
 * @param {Page} page - Browser page object
 * @returns {Promise<void>}
 */
async function navigateToOptimize(page) {
  console.log(`Navigating to: ${BASE_URL}`);
  await page.goto(BASE_URL);
}

/**
 * Perform login on Keycloak authentication page
 * @param {Page} page - Browser page object
 * @returns {Promise<number>} - Login duration in milliseconds
 */
async function performLogin(page) {
  const loginStart = Date.now();

  console.log(`Logging in as: ${USERNAME}`);

  // Fill in credentials
  await page.locator('input[name="username"]').fill(USERNAME);
  await page.locator('input[name="password"]').fill(PASSWORD);

  // Submit login form
  await page.locator('button[type="submit"]').click();

  const loginEnd = Date.now();
  const loginTime = loginEnd - loginStart;

  console.log(`✓ Login completed in ${loginTime}ms`);
  return loginTime;
}

/**
 * Wait for homepage to fully load and measure load time
 * @param {Page} page - Browser page object
 * @returns {Promise<number>} - Homepage load duration in milliseconds
 */
async function waitForHomepageLoad(page) {
  const homepageStart = Date.now();

  // Wait for the page to be fully loaded
  // You can adjust this selector based on what indicates a fully loaded Optimize homepage
  try {
    // Wait for network to be idle (no more than 2 connections for at least 500ms)
    await page.waitForLoadState('networkidle', { timeout: 10000 });

    // Optionally wait for specific elements that indicate the page is ready
    // await page.waitForSelector('[data-test-id="dashboard"]', { timeout: 10000 });

  } catch (error) {
    console.log(`Warning: Timeout waiting for page load: ${error.message}`);
  }

  const homepageEnd = Date.now();
  const homepageTime = homepageEnd - homepageStart;

  console.log(`✓ Homepage loaded in ${homepageTime}ms`);
  return homepageTime;
}

/**
 * Verify successful login by checking page title
 * @param {Page} page - Browser page object
 * @returns {Promise<boolean>} - True if login was successful
 */
async function verifyLogin(page) {
  const pageTitle = await page.title();
  console.log(`Page title: "${pageTitle}"`);

  const isSuccess = pageTitle === 'Optimize | Process dashboards and KPIs';

  check(page, {
    'login_successful': isSuccess,
    'page_title_correct': isSuccess,
  });

  return isSuccess;
}

/**
 * Capture screenshot of the current page
 * @param {Page} page - Browser page object
 * @param {string} filename - Screenshot filename
 * @returns {Promise<void>}
 */
async function captureScreenshot(page, filename = "screenshot.png") {
  try {
    await page.screenshot({ path: filename });
    console.log(`✓ Screenshot saved: ${filename}`);
  } catch (error) {
    console.log(`Warning: Failed to capture screenshot: ${error.message}`);
  }
}

export default async function() {
  const flowStart = Date.now();
  const page = await browser.newPage();

  try {
    // Step 1: Navigate to Optimize
    await navigateToOptimize(page);

    // Step 2: Perform login and measure duration
    const loginTime = await performLogin(page);
    loginDuration.add(loginTime);

    // Step 3: Wait for homepage to load and measure duration
    const homepageTime = await waitForHomepageLoad(page);
    homepageLoadTime.add(homepageTime);

    // Step 4: Verify successful login
    const loginSuccess = await verifyLogin(page);

    // Step 5: Capture screenshot
    await captureScreenshot(page, "optimize-homepage.png");

    // Calculate and log total flow duration
    const flowEnd = Date.now();
    const totalTime = flowEnd - flowStart;
    totalFlowDuration.add(totalTime);

    console.log('='.repeat(50));
    console.log('PERFORMANCE METRICS:');
    console.log(`  Login Duration:         ${loginTime}ms`);
    console.log(`  Homepage Load Time:     ${homepageTime}ms`);
    console.log(`  Total Flow Duration:    ${totalTime}ms`);
    console.log(`  Login Successful:       ${loginSuccess ? 'YES' : 'NO'}`);
    console.log('='.repeat(50));

  } catch (error) {
    console.log(`Error: ${error.message}`);
    await captureScreenshot(page, "error-screenshot.png");
    fail(`Browser iteration failed: ${error.message}`);
  } finally {
    await page.close();
  }

  sleep(1);
}

