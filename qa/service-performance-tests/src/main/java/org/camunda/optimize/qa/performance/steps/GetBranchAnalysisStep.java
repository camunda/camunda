package org.camunda.optimize.qa.performance.steps;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisDto;
import org.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisQueryDto;
import org.camunda.optimize.dto.optimize.query.report.filter.FilterDto;
import org.camunda.optimize.qa.performance.framework.PerfTestContext;
import org.camunda.optimize.qa.performance.framework.PerfTestStep;
import org.camunda.optimize.qa.performance.framework.PerfTestStepResult;
import org.camunda.optimize.qa.performance.util.PerfTestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.yandex.qatools.allure.annotations.Step;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;

public class GetBranchAnalysisStep extends PerfTestStep {

  private Logger logger = LoggerFactory.getLogger(this.getClass());
  private List<FilterDto> filter;

  public GetBranchAnalysisStep(List<FilterDto> filter) {
    this.filter = filter;
  }

  @Override
  @Step ("Get branch analysis from Optimize")
  public PerfTestStepResult execute(PerfTestContext context) {
    objectMapper = initMapper(context);
    CloseableHttpClient client = HttpClientBuilder.create().build();
    try {
      return getHeatmap(context, client);
    } catch (IOException | RuntimeException e) {
      throw new PerfTestException("Something went wrong while trying to execute the branch analysis!", e);
    } finally {
      try {
        client.close();
      } catch (IOException e) {
        logger.error("Could not close http client!", e);
      }
    }
  }

  private PerfTestStepResult<BranchAnalysisDto> getHeatmap(PerfTestContext context, CloseableHttpClient client) throws IOException {

    HttpPost post = setupPostRequest(context);

    CloseableHttpResponse response = client.execute(post);
    if(response.getStatusLine().getStatusCode() != 200 ) {
      logger.error(response.getStatusLine().toString());
      throw new PerfTestException("Post request was not successful!");
    }
    String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");

    PerfTestStepResult<BranchAnalysisDto> result = new PerfTestStepResult<>();
    BranchAnalysisDto responseDto = objectMapper.readValue(responseString, BranchAnalysisDto.class);
    result.setResult(responseDto);
    this.getClass().getSimpleName();

    return result;
  }

  private HttpPost setupPostRequest(PerfTestContext context) throws UnsupportedEncodingException, com.fasterxml.jackson.core.JsonProcessingException {
    HttpPost post = new HttpPost("http://localhost:8090/api/process-definition/correlation");
    post.addHeader("content-type", "application/json");
    post.addHeader("Authorization", "Bearer " + context.getConfiguration().getAuthorizationToken());
    BranchAnalysisQueryDto queryDto = new BranchAnalysisQueryDto();
    String processDefinitionKey = (String) context.getParameter("processDefinitionKey");
    String processDefinitionVersion = (String) context.getParameter("processDefinitionVersion");
    String gatewayId = (String) context.getParameter("gatewayActivityId");
    String endEventId = (String) context.getParameter("endActivityId");
    queryDto.setProcessDefinitionKey(processDefinitionKey);
    queryDto.setProcessDefinitionVersion(processDefinitionVersion);
    queryDto.setGateway(gatewayId);
    queryDto.setEnd(endEventId);
    queryDto.setFilter(filter);
    post.setEntity(new StringEntity(objectMapper.writeValueAsString(queryDto)));
    return post;
  }
}
