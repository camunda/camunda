package org.camunda.optimize.rest;

import org.camunda.optimize.dto.engine.ProcessDefinitionDto;
import org.camunda.optimize.service.es.ProcessDefinitionReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Path("/process-definition")
@Component
public class ProcessDefinition {

  @Autowired
  private ProcessDefinitionReader processDefinitionReader;

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public List<ProcessDefinitionDto> getProcessDefinitions() {
    return processDefinitionReader.getProcessDefinitions();
  }

  @GET
  @Path("/{id}/xml")
  public String getProcessDefinitionXml(@PathParam("id") String processDefinitionId){
    return processDefinitionReader.getProcessDefinitionXmls(processDefinitionId);
  }

}
