package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisDto;
import org.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisQueryDto;
import org.camunda.optimize.dto.optimize.query.definition.ExtendedProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.heatmap.HeatMapQueryDto;
import org.camunda.optimize.dto.optimize.query.heatmap.HeatMapResponseDto;
import org.camunda.optimize.dto.optimize.query.definition.ProcessDefinitionGroupOptimizeDto;
import org.camunda.optimize.dto.optimize.rest.FlowNodeNamesDto;
import org.camunda.optimize.rest.providers.Secured;
import org.camunda.optimize.service.es.reader.BranchAnalysisReader;
import org.camunda.optimize.service.es.reader.FrequencyHeatMapReader;
import org.camunda.optimize.service.es.reader.ProcessDefinitionReader;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Path("/flow-node")
@Component
public class FlowNodeRestService {

  @Autowired
  private ProcessDefinitionReader processDefinitionReader;

  @POST
  @Path("/{id}/flowNodeNames")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public FlowNodeNamesDto getFlowNodeNames(
      @PathParam("id") String processDefinitionId,
      List<String> nodeIds
  ) {
    return processDefinitionReader.getFlowNodeNames(processDefinitionId, nodeIds);
  }
}
