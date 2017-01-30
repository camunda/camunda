package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.HeatMapRequestDto;
import org.camunda.optimize.service.HeatMapService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.Map;

/**
 * @author Askar Akhmerov
 */
@Path("/heatmap")
@Component
public class HeatMap {

  @Autowired
  private HeatMapService heatMapService;

  @POST
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public Map<String, Long> getHeatMap(HeatMapRequestDto to) {
    return heatMapService.getHeatMap(to.getKey());
  }


  @POST
  @Path("/correlation")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public Long getCorrelation(HeatMapRequestDto to) {
    return heatMapService.activityCorrelation(to.getKey(), to.getCorrelationActivities());
  }


}
