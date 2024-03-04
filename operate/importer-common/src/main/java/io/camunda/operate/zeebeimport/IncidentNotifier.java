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
package io.camunda.operate.zeebeimport;

import static io.camunda.operate.util.CollectionUtil.asMap;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.cache.ProcessCache;
import io.camunda.operate.entities.IncidentEntity;
import io.camunda.operate.entities.ProcessEntity;
import io.camunda.operate.property.OperateProperties;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/** Class that sends notifications about the created incidents on configured URL. */
@Component
public class IncidentNotifier {

  protected static final String FIELD_NAME_ALERTS = "alerts";
  protected static final String FIELD_NAME_MESSAGE = "message";
  protected static final String MESSAGE = "Incident created";
  protected static final String FIELD_NAME_ID = "id";
  protected static final String FIELD_NAME_PROCESS_INSTANCE_ID = "processInstanceId";
  protected static final String FIELD_NAME_CREATION_TIME = "creationTime";
  protected static final String FIELD_NAME_STATE = "state";
  protected static final String FIELD_NAME_ERROR_MESSAGE = "errorMessage";
  protected static final String FIELD_NAME_ERROR_TYPE = "errorType";
  protected static final String FIELD_NAME_FLOW_NODE_ID = "flowNodeId";
  protected static final String FIELD_NAME_FLOW_NODE_INSTANCE_KEY = "flowNodeInstanceKey";
  protected static final String FIELD_NAME_JOB_KEY = "jobKey";
  protected static final String FIELD_NAME_PROCESS_KEY = "processDefinitionKey";
  protected static final String FIELD_NAME_BPMN_PROCESS_ID = "bpmnProcessId";
  protected static final String FIELD_NAME_PROCESS_NAME = "processName";
  protected static final String FIELD_NAME_PROCESS_VERSION = "processVersion";
  private static final Logger logger = LoggerFactory.getLogger(IncidentNotifier.class);
  @Autowired private OperateProperties operateProperties;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private M2mTokenManager m2mTokenManager;

  @Autowired
  @Qualifier("incidentNotificationRestTemplate")
  private RestTemplate restTemplate;

  @Autowired private ProcessCache processCache;

  public void notifyOnIncidents(final List<IncidentEntity> incidents) {
    try {
      HttpStatusCode status = notifyOnIncidents(incidents, m2mTokenManager.getToken());

      if (status.is2xxSuccessful()) {
        logger.debug("Incident notification is sent");
      } else if (status.isSameCodeAs(HttpStatus.UNAUTHORIZED)) {
        logger.debug("Incident notification recieved 401 response");
        // retry
        status = notifyOnIncidents(incidents, m2mTokenManager.getToken(true));
        if (status.is2xxSuccessful()) {
          logger.debug("Incident notification is sent");
        } else {
          logger.error("Failed to send incident notification. Response status: " + status);
        }
      } else {
        logger.error("Failed to send incident notification. Response status: " + status);
      }
    } catch (final JsonProcessingException e) {
      logger.error("Failed to create incident notification request: " + e.getMessage(), e);
    } catch (final Exception e) {
      logger.error("Failed to notify on incidents: " + e.getMessage(), e);
    }
  }

  private HttpStatusCode notifyOnIncidents(
      final List<IncidentEntity> incidents, final String m2mToken) throws JsonProcessingException {
    final String webhookURL = operateProperties.getAlert().getWebhook();
    final String payload = getIncidentsAsJSON(incidents);
    final HttpHeaders headers = new HttpHeaders();
    headers.setContentType(APPLICATION_JSON);
    headers.setBearerAuth(m2mToken);

    final HttpEntity<String> request = new HttpEntity<>(payload, headers);
    final ResponseEntity<String> response =
        restTemplate.postForEntity(webhookURL, request, String.class);
    return response.getStatusCode();
  }

  private String getIncidentsAsJSON(final List<IncidentEntity> incidents)
      throws JsonProcessingException {
    final List<Map<String, Object>> incidentList = new ArrayList<>();
    for (final IncidentEntity inc : incidents) {
      final Map<String, Object> incidentFields = new HashMap<>();
      incidentFields.put(FIELD_NAME_MESSAGE, MESSAGE);
      incidentFields.put(FIELD_NAME_ID, inc.getId());
      incidentFields.put(
          FIELD_NAME_PROCESS_INSTANCE_ID, String.valueOf(inc.getProcessInstanceKey()));
      incidentFields.put(FIELD_NAME_CREATION_TIME, inc.getCreationTime());
      incidentFields.put(FIELD_NAME_STATE, inc.getState());
      incidentFields.put(FIELD_NAME_ERROR_MESSAGE, inc.getErrorMessage());
      incidentFields.put(FIELD_NAME_ERROR_TYPE, inc.getErrorType());
      incidentFields.put(FIELD_NAME_FLOW_NODE_ID, inc.getFlowNodeId());
      incidentFields.put(FIELD_NAME_FLOW_NODE_INSTANCE_KEY, inc.getFlowNodeInstanceKey());
      incidentFields.put(FIELD_NAME_JOB_KEY, inc.getJobKey());
      incidentFields.put(FIELD_NAME_PROCESS_KEY, inc.getProcessDefinitionKey());
      final Optional<ProcessEntity> process =
          processCache.findOrWaitProcess(inc.getProcessDefinitionKey(), 2, 1000L);
      if (process.isPresent()) {
        incidentFields.put(FIELD_NAME_BPMN_PROCESS_ID, process.get().getBpmnProcessId());
        incidentFields.put(FIELD_NAME_PROCESS_NAME, process.get().getName());
        incidentFields.put(FIELD_NAME_PROCESS_VERSION, process.get().getVersion());
      }
      incidentList.add(incidentFields);
    }
    return objectMapper.writeValueAsString(asMap(FIELD_NAME_ALERTS, incidentList));
  }
}
