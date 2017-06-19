package org.camunda.optimize.jetty;

import org.camunda.bpm.licensecheck.InvalidLicenseException;
import org.camunda.optimize.service.license.LicenseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

public class LicenseFilter implements Filter {

  private Logger logger = LoggerFactory.getLogger(LicenseManager.class);

  private static final String INDEX_PAGE = "/";
  private static final String INDEX_HTML_PAGE = "/index.html";
  private static final String LOGIN_PAGE = "/login";
  private static final String LICENSE_PAGE = "/license.html";

  private LicenseManager licenseManager;

  private SpringAwareServletConfiguration awareDelegate;
  private String licenseFromFile;

  public LicenseFilter(SpringAwareServletConfiguration awareDelegate) {
    this.awareDelegate = awareDelegate;
  }

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
    // nothing to do here
    try {
      licenseFromFile = readFileToString("OptimizeLicense.txt");
    } catch (Exception ignore) {
      // do nothing
    }
  }

  private String readFileToString(String filePath) throws IOException, URISyntaxException {
    InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(filePath);
    ByteArrayOutputStream result = new ByteArrayOutputStream();
    byte[] buffer = new byte[1024];
    int length;
    while ((length = inputStream.read(buffer)) != -1) {
      result.write(buffer, 0, length);
    }
    return result.toString(StandardCharsets.UTF_8.name());
  }

  /**
   * Before the user can access the login page a license check is performed.
   * Whenever there is an invalid or no license, the user gets redirected to the license page.
   */
  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
    setLicenseManager();
    HttpServletResponse servletResponse = (HttpServletResponse) response;
    HttpServletRequest servletRequest = (HttpServletRequest) request;
    String requestPath = servletRequest.getServletPath().toLowerCase();
    if (isIndexPage(requestPath) || isLoginPage(requestPath)) {
      String licenseAsString = retrieveLicense();
      if(!licenseManager.isValidOptimizeLicense(licenseAsString)) {
        logger.warn("Given License is invalid or not available, redirecting to license page!");
        servletResponse.sendRedirect(LICENSE_PAGE);
        return;
      }
    }
    chain.doFilter(request, response);
  }

  private String retrieveLicense() {
    if (licenseFromFile != null) {
      return licenseFromFile;
    }
    String license = null;
    try {
      license = licenseManager.retrieveStoredOptimizeLicense();
    } catch (InvalidLicenseException ignored) {
      // do nothing
    }
    return license;
  }

  private void setLicenseManager() {
    if (licenseManager == null) {
      licenseManager = awareDelegate.getApplicationContext().getBean(LicenseManager.class);
    }
  }

  private boolean isIndexPage(String requestPath) {
    return (INDEX_PAGE).equals(requestPath) || requestPath.startsWith(INDEX_HTML_PAGE);
  }

  private boolean isLoginPage(String requestPath) {
    return requestPath.startsWith(LOGIN_PAGE);
  }

  @Override
  public void destroy() {
    // nothing to do here
  }
}
