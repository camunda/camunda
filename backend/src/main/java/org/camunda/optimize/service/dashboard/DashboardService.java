package org.camunda.optimize.service.dashboard;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionUpdateDto;
import org.camunda.optimize.service.es.reader.DashboardReader;
import org.camunda.optimize.service.es.writer.DashboardWriter;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.security.SharingService;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * @author Askar Akhmerov
 */
@Component
public class DashboardService {

  @Autowired
  private DashboardWriter dashboardWriter;

  @Autowired
  private DashboardReader dashboardReader;

  @Autowired
  private SharingService sharingService;

  public IdDto createNewDashboardAndReturnId(String userId) {
    return dashboardWriter.createNewDashboardAndReturnId(userId);
  }

  public void updateDashboard(DashboardDefinitionDto updatedDashboard, String userId) throws OptimizeException, JsonProcessingException {
    DashboardDefinitionDto updateDto = new DashboardDefinitionDto();
    updateDto.setId(updatedDashboard.getId());
    updateDto.setLastModified(LocalDateUtil.getCurrentDateTime());
    updateDto.setOwner(updatedDashboard.getOwner());
    updateDto.setName(updatedDashboard.getName());
    updateDto.setReports(updatedDashboard.getReports());
    updateDto.setLastModifier(userId);
    updateDto.setLastModified(LocalDateUtil.getCurrentDateTime());
    dashboardWriter.updateDashboard(updateDto);
    sharingService.adjustDashboardShares(updateDto);
  }

  public List<DashboardDefinitionDto> getDashboardDefinitions() throws IOException {
    return dashboardReader.getAllDashboards();
  }

  public DashboardDefinitionDto getDashboardDefinition(String dashboardId) {
    return dashboardReader.getDashboard(dashboardId);
  }

  public void deleteDashboard(String dashboardId) {
    dashboardWriter.deleteDashboard(dashboardId);
  }
}
