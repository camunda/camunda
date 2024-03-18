/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.util;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Predicate;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

public class MetricAssert {

  //  public static void assertThatMetricsAreDisabledFrom(MockMvc mockMvc) {
  //    MockHttpServletRequestBuilder request = get(ENDPOINT);
  //    try {
  //      mockMvc.perform(request)
  //          .andExpect(status().is(404));
  //    } catch (Exception e) {
  //      throw new RuntimeException("Exception while asserting:" + e.getMessage(), e);
  //    }
  //  }

  public static void assertThatMetricsFrom(MockMvc mockMvc, Matcher<? super String> matcher) {
    final MockHttpServletRequestBuilder request = get("/actuator/prometheus");
    try {
      mockMvc.perform(request).andExpect(status().isOk()).andExpect(content().string(matcher));
    } catch (Exception e) {
      throw new RuntimeException("Exception while asserting:" + e.getMessage(), e);
    }
  }

  public static final class ValueMatcher extends BaseMatcher {

    private final String metricName;
    private final Predicate<Double> valueMatcher;

    public ValueMatcher(String metricName, Predicate<Double> valueMatcher) {
      this.metricName = metricName.toLowerCase();
      this.valueMatcher = valueMatcher;
    }

    @Override
    public boolean matches(Object o) {
      final Double metricValue = getMetricValue(o);
      if (metricValue != null) {
        return valueMatcher.test(metricValue);
      }
      return false;
    }

    public Double getMetricValue(Object o) {
      final Optional<String> metricString = getMetricString(o);
      if (metricString.isPresent()) {
        final String[] oneMetric = metricString.get().split(" ");
        if (oneMetric.length > 1) {
          return Double.valueOf(oneMetric[1]);
        }
      }
      return null;
    }

    public Optional<String> getMetricString(Object o) {
      final String s = (String) o;
      final String[] strings = s.split("\\n");
      return Arrays.stream(strings)
          .filter(str -> str.toLowerCase().contains(metricName) && !str.startsWith("#"))
          .findFirst();
    }

    @Override
    public void describeTo(Description description) {}
  }
}
