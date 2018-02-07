package org.camunda.optimize.service.security;

import org.camunda.optimize.dto.optimize.query.report.result.ReportResultDto;
import org.camunda.optimize.dto.optimize.query.sharing.EvaluatedReportShareDto;
import org.camunda.optimize.dto.optimize.query.sharing.SharingDto;
import org.camunda.optimize.service.es.reader.SharingReader;
import org.camunda.optimize.service.es.writer.SharingWriter;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.report.ReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

/**
 * @author Askar Akhmerov
 */
@Component
public class SharingService  {
  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  private SharingWriter sharingWriter;

  @Autowired
  private SharingReader sharingReader;

  @Autowired
  private ReportService reportService;

  public String crateNewShare(SharingDto createSharingDto) {
    String result;
    Optional<SharingDto> existing = sharingReader.findSharedResource(createSharingDto);

    result = existing
      .map(SharingDto::getId)
      .orElseGet(() -> sharingWriter.saveShare(createSharingDto).getId());

    return result;
  }

  public void deleteShare(String shareId) {
    sharingWriter.deleteShare(shareId);
  }

  public SharingDto findShareForResource(String resourceId) {
    Optional<SharingDto> shareForResource = sharingReader.findShareForResource(resourceId);
    return shareForResource.orElse(null);
  }

  public Optional<EvaluatedReportShareDto> evaluate(String shareId) {
    Optional<EvaluatedReportShareDto> result = Optional.empty();
    Optional<SharingDto> base = sharingReader.findShare(shareId);

    if (base.isPresent()) {
      EvaluatedReportShareDto wrapped = new EvaluatedReportShareDto(base.get());
      try {
        ReportResultDto reportResultDto = reportService.evaluateSavedReport(wrapped.getResourceId());
        wrapped.setReport(reportResultDto);
        result = Optional.of(wrapped);
      } catch (IOException e) {
        logger.error("can't evaluate shared report []", wrapped.getResourceId());
      } catch (OptimizeException e) {
        logger.error("can't evaluate shared report []", wrapped.getResourceId());
      }
    } else {
      throw new OptimizeRuntimeException("share [" + shareId + "] does not exist");
    }

    return result;
  }

  public SharingDto findShare(String shareId) {
    return sharingReader.findShare(shareId).orElse(null);
  }
}
