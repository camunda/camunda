package org.camunda.bpm.platform.servlet.purge;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.repository.Deployment;
import org.camunda.bpm.engine.repository.DeploymentQuery;
import org.camunda.bpm.engine.rest.dto.runtime.ProcessInstanceDto;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 *
 * Servlet that actually purges the database on GET request to root endpoint
 * 
 * @author Askar Akhmerov
 */
@WebServlet(name="Deploy ServiceTask Process", urlPatterns={"/serviceTask/*"})
public class DeployServiceTaskProcessServlet extends HttpServlet {
  private ProcessEngine processEngine;
  private DeployProcessApplication myPa;
  private ObjectMapper objectMapper;

  @Override
  public void init(ServletConfig config) throws ServletException {
    super.init(config);
    processEngine = (ProcessEngine) config.getServletContext().getAttribute("processEngine");
    myPa = (DeployProcessApplication) config.getServletContext().getAttribute("processApplication");
    objectMapper = new ObjectMapper();
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    String key = "testProcess";

    BpmnModelInstance model = Bpmn.createExecutableProcess(key)
      .name("ServiceTask")
        .startEvent()
          .serviceTask()
            .camundaExpression("${true}")
        .endEvent()
        .done();

    RepositoryService repositoryService = processEngine.getRepositoryService();
    repositoryService.createDeploymentQuery();

    repositoryService
        .createDeployment(myPa.getReference())
        .name("awesome-it-process")
        .addModelInstance("process.bpmn", model)
        .deploy();

    ProcessInstance testProcess = processEngine.getRuntimeService().startProcessInstanceByKey("testProcess");

    resp.setStatus(200);
    resp.setContentType("application/json");
    resp.setCharacterEncoding("UTF-8");
    resp.getWriter().println(objectMapper.writeValueAsString(new ProcessInstanceDto(testProcess)));
    resp.getWriter().flush();
  }
}
